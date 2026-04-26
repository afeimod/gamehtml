package com.snakegame.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.snakegame.app.R
import com.snakegame.app.game.Difficulty
import com.snakegame.app.game.SnakeGameView

/**
 * 游戏主界面
 * 包含游戏视图、暂停按钮和返回菜单按钮
 */
class GameActivity : AppCompatActivity() {

    private lateinit var gameView: SnakeGameView
    private lateinit var scoreText: TextView
    private lateinit var pauseButton: Button
    private lateinit var menuButton: Button
    
    private var isPaused = false
    private lateinit var currentDifficulty: Difficulty
    
    companion object {
        const val EXTRA_DIFFICULTY = "extra_difficulty"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 获取难度设置
        val difficultyName = intent.getStringExtra(EXTRA_DIFFICULTY) ?: Difficulty.EASY.name
        currentDifficulty = Difficulty.valueOf(difficultyName)
        
        initViews()
        setupGame()
        setupClickListeners()
    }
    
    /**
     * 初始化视图组件
     */
    private fun initViews() {
        gameView = findViewById(R.id.snakeGameView)
        scoreText = findViewById(R.id.scoreText)
        pauseButton = findViewById(R.id.btnPause)
        menuButton = findViewById(R.id.btnMenu)
        
        // 根据难度设置标题
        val titleText = findViewById<TextView>(R.id.tvTitle)
        titleText.visibility = View.VISIBLE
        titleText.text = getString(R.string.game_title, getDifficultyDisplayName())
        
        // 显示暂停和菜单按钮
        pauseButton.visibility = View.VISIBLE
        menuButton.visibility = View.VISIBLE
        
        // 根据难度调整游戏参数
        applyDifficultySettings()
    }
    
    /**
     * 根据难度应用游戏设置
     */
    private fun applyDifficultySettings() {
        when (currentDifficulty) {
            Difficulty.EASY -> {
                gameView.setGameSpeed(200L)      // 慢速
                gameView.setObstacleCount(0)      // 无障碍物
                gameView.setEnableGrowth(true)    // 正常生长
            }
            Difficulty.NORMAL -> {
                gameView.setGameSpeed(150L)       // 中速
                gameView.setObstacleCount(5)      // 少量障碍物
                gameView.setEnableGrowth(true)    // 正常生长
            }
            Difficulty.HARD -> {
                gameView.setGameSpeed(100L)       // 快速
                gameView.setObstacleCount(10)     // 大量障碍物
                gameView.setEnableGrowth(false)   // 吃食物不生长，更难
            }
        }
    }
    
    /**
     * 设置游戏回调
     */
    private fun setupGame() {
        // 分数变化回调
        gameView.onScoreChanged = { score ->
            runOnUiThread {
                scoreText.text = getString(R.string.score_format, score)
            }
        }
        
        // 游戏结束回调
        gameView.onGameOver = {
            runOnUiThread {
                showGameOverDialog()
            }
        }
        
        // 启动游戏
        gameView.startGame()
    }
    
    /**
     * 设置点击监听器
     */
    private fun setupClickListeners() {
        // 暂停按钮
        pauseButton.setOnClickListener {
            togglePause()
        }
        
        // 返回菜单按钮
        menuButton.setOnClickListener {
            showExitConfirmDialog()
        }
        
        // 虚拟方向键
        findViewById<Button>(R.id.btnUp).setOnClickListener {
            if (!isPaused) gameView.setDirection(com.snakegame.app.game.Direction.UP)
        }
        findViewById<Button>(R.id.btnDown).setOnClickListener {
            if (!isPaused) gameView.setDirection(com.snakegame.app.game.Direction.DOWN)
        }
        findViewById<Button>(R.id.btnLeft).setOnClickListener {
            if (!isPaused) gameView.setDirection(com.snakegame.app.game.Direction.LEFT)
        }
        findViewById<Button>(R.id.btnRight).setOnClickListener {
            if (!isPaused) gameView.setDirection(com.snakegame.app.game.Direction.RIGHT)
        }
    }
    
    /**
     * 切换暂停状态
     */
    private fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            gameView.pauseGame()
            pauseButton.text = getString(R.string.btn_resume)
        } else {
            gameView.resumeGame()
            pauseButton.text = getString(R.string.btn_pause)
        }
    }
    
    /**
     * 显示退出确认对话框
     */
    private fun showExitConfirmDialog() {
        if (isPaused) {
            gameView.pauseGame()
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_exit_title)
            .setMessage(R.string.confirm_exit_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                returnToMenu()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                if (isPaused) {
                    gameView.pauseGame()
                }
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 返回主菜单
     */
    private fun returnToMenu() {
        gameView.pauseGame()
        finish()
    }
    
    /**
     * 显示游戏结束对话框
     */
    private fun showGameOverDialog() {
        val score = gameView.getScore()
        val highScore = getHighScore()
        
        var message = getString(R.string.final_score_format, score, highScore)
        if (score >= highScore && score > 0) {
            message += "\n" + getString(R.string.new_record)
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.game_over)
            .setMessage(message)
            .setPositiveButton(R.string.restart) { _, _ ->
                gameView.restartGame()
                isPaused = false
                pauseButton.text = getString(R.string.btn_pause)
            }
            .setNegativeButton(R.string.return_to_menu) { _, _ ->
                returnToMenu()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 获取最高分
     */
    private fun getHighScore(): Int {
        val prefs = getSharedPreferences("snake_game", MODE_PRIVATE)
        return prefs.getInt("high_score_${currentDifficulty.name}", 0)
    }
    
    /**
     * 保存最高分
     */
    private fun saveHighScore(score: Int) {
        val prefs = getSharedPreferences("snake_game", MODE_PRIVATE)
        val currentHighScore = prefs.getInt("high_score_${currentDifficulty.name}", 0)
        if (score > currentHighScore) {
            prefs.edit().putInt("high_score_${currentDifficulty.name}", score).apply()
        }
    }
    
    /**
     * 获取难度显示名称
     */
    private fun getDifficultyDisplayName(): String {
        return when (currentDifficulty) {
            Difficulty.EASY -> getString(R.string.difficulty_easy)
            Difficulty.NORMAL -> getString(R.string.difficulty_normal)
            Difficulty.HARD -> getString(R.string.difficulty_hard)
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (!isPaused && !gameView.isGameOver()) {
            gameView.pauseGame()
            isPaused = true
            pauseButton.text = getString(R.string.btn_resume)
        }
    }
    
    override fun onBackPressed() {
        showExitConfirmDialog()
    }
}