package com.snakegame.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.snakegame.app.R

/**
 * 主菜单界面
 * 提供游戏入口、难度选择和退出功能
 */
class MenuActivity : AppCompatActivity() {

    // 难度等级
    private var selectedDifficulty = Difficulty.EASY
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        
        initViews()
        setupClickListeners()
    }
    
    /**
     * 初始化视图组件
     */
    private fun initViews() {
        val btnEasy = findViewById<Button>(R.id.btnEasy)
        val btnNormal = findViewById<Button>(R.id.btnNormal)
        val btnHard = findViewById<Button>(R.id.btnHard)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnExit = findViewById<Button>(R.id.btnExit)
        val tvDifficultyLabel = findViewById<TextView>(R.id.tvDifficultyLabel)
        
        // 默认选中简单难度
        updateDifficultySelection(Difficulty.EASY, btnEasy, btnNormal, btnHard, tvDifficultyLabel)
    }
    
    /**
     * 设置点击监听器
     */
    private fun setupClickListeners() {
        // 简单难度
        findViewById<Button>(R.id.btnEasy).setOnClickListener {
            updateDifficultySelection(Difficulty.EASY, 
                findViewById(R.id.btnEasy), 
                findViewById(R.id.btnNormal), 
                findViewById(R.id.btnHard),
                findViewById(R.id.tvDifficultyLabel))
        }
        
        // 普通难度
        findViewById<Button>(R.id.btnNormal).setOnClickListener {
            updateDifficultySelection(Difficulty.NORMAL, 
                findViewById(R.id.btnEasy), 
                findViewById(R.id.btnNormal), 
                findViewById(R.id.btnHard),
                findViewById(R.id.tvDifficultyLabel))
        }
        
        // 困难难度
        findViewById<Button>(R.id.btnHard).setOnClickListener {
            updateDifficultySelection(Difficulty.HARD, 
                findViewById(R.id.btnEasy), 
                findViewById(R.id.btnNormal), 
                findViewById(R.id.btnHard),
                findViewById(R.id.tvDifficultyLabel))
        }
        
        // 开始游戏按钮
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startGame()
        }
        
        // 退出游戏按钮
        findViewById<Button>(R.id.btnExit).setOnClickListener {
            finish()
        }
    }
    
    /**
     * 更新难度选择状态
     */
    private fun updateDifficultySelection(
        difficulty: Difficulty,
        btnEasy: Button,
        btnNormal: Button,
        btnHard: Button,
        tvLabel: TextView
    ) {
        selectedDifficulty = difficulty
        
        // 重置所有按钮样式
        btnEasy.setBackgroundResource(R.drawable.btn_difficulty_normal)
        btnNormal.setBackgroundResource(R.drawable.btn_difficulty_normal)
        btnHard.setBackgroundResource(R.drawable.btn_difficulty_normal)
        
        // 高亮选中按钮
        when (difficulty) {
            Difficulty.EASY -> {
                btnEasy.setBackgroundResource(R.drawable.btn_difficulty_selected)
                tvLabel.text = getString(R.string.difficulty_easy_desc)
            }
            Difficulty.NORMAL -> {
                btnNormal.setBackgroundResource(R.drawable.btn_difficulty_selected)
                tvLabel.text = getString(R.string.difficulty_normal_desc)
            }
            Difficulty.HARD -> {
                btnHard.setBackgroundResource(R.drawable.btn_difficulty_selected)
                tvLabel.text = getString(R.string.difficulty_hard_desc)
            }
        }
    }
    
    /**
     * 开始游戏
     */
    private fun startGame() {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra(GameActivity.EXTRA_DIFFICULTY, selectedDifficulty.name)
        startActivity(intent)
    }
    
    /**
     * 难度等级枚举
     */
    enum class Difficulty {
        EASY,    // 简单 - 无障碍物，标准速度
        NORMAL,  // 普通 - 少量随机障碍物
        HARD     // 困难 - 大量随机障碍物，更快速度
    }
    
    override fun onBackPressed() {
        // 按返回键直接退出
        finish()
    }
}