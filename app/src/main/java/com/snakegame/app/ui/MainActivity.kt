package com.snakegame.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.snakegame.app.R
import com.snakegame.app.game.Direction
import com.snakegame.app.game.SnakeGameView

/**
 * 贪吃蛇游戏主活动
 * 负责游戏界面和虚拟按键控制
 */
class MainActivity : AppCompatActivity() {

    // 游戏视图
    private lateinit var gameView: SnakeGameView
    
    // 分数显示
    private lateinit var scoreText: TextView
    
    // 虚拟按键按钮
    private lateinit var btnUp: Button
    private lateinit var btnDown: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化视图
        initViews()
        
        // 设置游戏回调
        setupGameCallbacks()
        
        // 设置虚拟按键
        setupControls()
        
        // 开始游戏
        gameView.startGame()
    }
    
    /**
     * 初始化视图
     */
    private fun initViews() {
        gameView = findViewById(R.id.snakeGameView)
        scoreText = findViewById(R.id.scoreText)
        btnUp = findViewById(R.id.btnUp)
        btnDown = findViewById(R.id.btnDown)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)
        
        // 根据屏幕高度调整游戏区域
        adjustGameGrid()
    }
    
    /**
     * 调整游戏网格
     */
    private fun adjustGameGrid() {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        
        // 计算适合的网格高度
        // 保留底部按键空间（约200dp）和顶部分数空间（约80dp）
        val availableHeight = screenHeight - dpToPx(280)
        val cellHeight = availableHeight / 30f
        val cellWidth = screenWidth / 20f
        val cellSize = minOf(cellHeight, cellWidth)
        val newGridHeight = (availableHeight / cellSize).toInt()
        
        gameView.setGridHeight(newGridHeight.coerceAtLeast(20))
    }
    
    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    /**
     * 设置游戏回调
     */
    private fun setupGameCallbacks() {
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
    }
    
    /**
     * 设置虚拟按键
     */
    private fun setupControls() {
        // 上按钮
        btnUp.setOnClickListener {
            gameView.setDirection(Direction.UP)
        }
        
        // 下按钮
        btnDown.setOnClickListener {
            gameView.setDirection(Direction.DOWN)
        }
        
        // 左按钮
        btnLeft.setOnClickListener {
            gameView.setDirection(Direction.LEFT)
        }
        
        // 右按钮
        btnRight.setOnClickListener {
            gameView.setDirection(Direction.RIGHT)
        }
        
        // 长按加速（可选功能）
        btnUp.setOnLongClickListener {
            // 可以添加加速功能
            true
        }
    }
    
    /**
     * 显示游戏结束对话框
     */
    private fun showGameOverDialog() {
        val score = gameView.getScore()
        AlertDialog.Builder(this)
            .setTitle(R.string.game_over)
            .setMessage(getString(R.string.final_score_format, score, score))
            .setPositiveButton(R.string.restart) { _, _ ->
                gameView.restartGame()
            }
            .setNegativeButton(R.string.exit) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onPause() {
        super.onPause()
        gameView.pauseGame()
    }
    
    override fun onResume() {
        super.onResume()
        if (!gameView.isGameOver()) {
            gameView.resumeGame()
        }
    }
}