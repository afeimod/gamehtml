package com.snakegame.app.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.snakegame.app.R

/**
 * 贪吃蛇游戏视图
 * 负责游戏绘制和游戏逻辑
 */
class SnakeGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 游戏参数
    private val gridWidth = 20  // 网格宽度（列数）
    private var gridHeight = 30  // 网格高度（行数）
    
    // 游戏状态
    private var snake: MutableList<Point> = mutableListOf()
    private var food: Point = Point(0, 0)
    private var direction: Direction = Direction.RIGHT
    private var isGameRunning = false
    private var isGameOver = false
    private var score = 0
    
    // 画笔
    private val backgroundPaint = Paint()
    private val gridPaint = Paint()
    private val snakeHeadPaint = Paint()
    private val snakeBodyPaint = Paint()
    private val foodPaint = Paint()
    
    // 单元格尺寸
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    
    // 游戏循环
    private val handler = Handler(Looper.getMainLooper())
    private val gameLoopRunnable = object : Runnable {
        override fun run() {
            if (isGameRunning && !isGameOver) {
                moveSnake()
                checkCollision()
                if (!isGameOver) {
                    invalidate()
                    handler.postDelayed(this, GAME_SPEED)
                }
            }
        }
    }
    
    // 回调接口
    var onScoreChanged: ((Int) -> Unit)? = null
    var onGameOver: (() -> Unit)? = null
    
    // 游戏速度（毫秒）
    companion object {
        private const val GAME_SPEED = 200L
        private const val INITIAL_SNAKE_LENGTH = 3  // 初始蛇长度
    }
    
    init {
        initPaints()
        initGame()
    }
    
    /**
     * 初始化画笔
     */
    private fun initPaints() {
        // 背景画笔
        backgroundPaint.color = context.getColor(R.color.game_background)
        backgroundPaint.style = Paint.Style.FILL
        
        // 网格画笔
        gridPaint.color = context.getColor(R.color.grid_line)
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = 1f
        
        // 蛇头画笔
        snakeHeadPaint.color = context.getColor(R.color.snake_head)
        snakeHeadPaint.style = Paint.Style.FILL
        
        // 蛇身画笔
        snakeBodyPaint.color = context.getColor(R.color.snake_body)
        snakeBodyPaint.style = Paint.Style.FILL
        
        // 食物画笔
        foodPaint.color = context.getColor(R.color.food)
        foodPaint.style = Paint.Style.FILL
    }
    
    /**
     * 初始化游戏
     */
    fun initGame() {
        // 重置蛇
        snake.clear()
        val startX = gridWidth / 2
        val startY = gridHeight / 2
        for (i in 0 until INITIAL_SNAKE_LENGTH) {
            snake.add(Point(startX - i, startY))
        }
        
        // 重置方向
        direction = Direction.RIGHT
        
        // 生成食物
        generateFood()
        
        // 重置分数
        score = 0
        
        // 重置游戏状态
        isGameOver = false
        isGameRunning = true
        
        // 通知分数变化
        onScoreChanged?.invoke(score)
        
        // 启动游戏循环
        handler.removeCallbacks(gameLoopRunnable)
        handler.post(gameLoopRunnable)
    }
    
    /**
     * 生成食物
     */
    private fun generateFood() {
        val random = java.util.Random()
        var newFood: Point
        do {
            newFood = Point(
                random.nextInt(gridWidth),
                random.nextInt(gridHeight)
            )
        } while (snake.contains(newFood))
        food = newFood
    }
    
    /**
     * 移动蛇
     */
    private fun moveSnake() {
        val head = snake.first()
        val newHead = when (direction) {
            Direction.UP -> Point(head.x, head.y - 1)
            Direction.DOWN -> Point(head.x, head.y + 1)
            Direction.LEFT -> Point(head.x - 1, head.y)
            Direction.RIGHT -> Point(head.x + 1, head.y)
        }
        
        // 添加新头部
        snake.add(0, newHead)
        
        // 检查是否吃到食物
        if (newHead == food) {
            score++
            onScoreChanged?.invoke(score)
            generateFood()
        } else {
            // 没吃到食物，移除尾部
            snake.removeLast()
        }
    }
    
    /**
     * 检查碰撞
     */
    private fun checkCollision() {
        val head = snake.first()
        
        // 检查是否撞墙
        if (head.x < 0 || head.x >= gridWidth || head.y < 0 || head.y >= gridHeight) {
            endGame()
            return
        }
        
        // 检查是否撞到自己
        if (snake.drop(1).contains(head)) {
            endGame()
            return
        }
    }
    
    /**
     * 结束游戏
     */
    private fun endGame() {
        isGameOver = true
        isGameRunning = false
        handler.removeCallbacks(gameLoopRunnable)
        onGameOver?.invoke()
    }
    
    /**
     * 设置方向
     */
    fun setDirection(newDirection: Direction) {
        // 防止180度转向
        val currentDirection = direction
        when (newDirection) {
            Direction.UP -> {
                if (currentDirection != Direction.DOWN) {
                    direction = newDirection
                }
            }
            Direction.DOWN -> {
                if (currentDirection != Direction.UP) {
                    direction = newDirection
                }
            }
            Direction.LEFT -> {
                if (currentDirection != Direction.RIGHT) {
                    direction = newDirection
                }
            }
            Direction.RIGHT -> {
                if (currentDirection != Direction.LEFT) {
                    direction = newDirection
                }
            }
        }
    }
    
    /**
     * 开始游戏
     */
    fun startGame() {
        if (!isGameRunning && !isGameOver) {
            initGame()
        }
    }
    
    /**
     * 重新开始游戏
     */
    fun restartGame() {
        initGame()
    }
    
    /**
     * 暂停游戏
     */
    fun pauseGame() {
        isGameRunning = false
        handler.removeCallbacks(gameLoopRunnable)
    }
    
    /**
     * 恢复游戏
     */
    fun resumeGame() {
        if (!isGameOver) {
            isGameRunning = true
            handler.post(gameLoopRunnable)
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateCellSize(w, h)
    }
    
    /**
     * 计算单元格大小
     */
    private fun calculateCellSize(width: Int, height: Int) {
        // 根据可用空间计算单元格大小
        val cellWidth = width.toFloat() / gridWidth
        val cellHeight = height.toFloat() / gridHeight
        cellSize = minOf(cellWidth, cellHeight)
        
        // 计算偏移量使游戏区域居中
        offsetX = (width - cellSize * gridWidth) / 2
        offsetY = (height - cellSize * gridHeight) / 2
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        // 绘制游戏区域背景
        val gameRect = RectF(
            offsetX, offsetY,
            offsetX + cellSize * gridWidth,
            offsetY + cellSize * gridHeight
        )
        canvas.drawRect(gameRect, backgroundPaint)
        
        // 绘制网格
        drawGrid(canvas)
        
        // 绘制食物
        drawFood(canvas)
        
        // 绘制蛇
        drawSnake(canvas)
    }
    
    /**
     * 绘制网格
     */
    private fun drawGrid(canvas: Canvas) {
        gridPaint.strokeWidth = 1f
        gridPaint.color = context.getColor(R.color.grid_line)
        
        // 绘制垂直线
        for (x in 0..gridWidth) {
            val startX = offsetX + x * cellSize
            canvas.drawLine(
                startX, offsetY,
                startX, offsetY + gridHeight * cellSize,
                gridPaint
            )
        }
        
        // 绘制水平线
        for (y in 0..gridHeight) {
            val startY = offsetY + y * cellSize
            canvas.drawLine(
                offsetX, startY,
                offsetX + gridWidth * cellSize, startY,
                gridPaint
            )
        }
    }
    
    /**
     * 绘制食物
     */
    private fun drawFood(canvas: Canvas) {
        val rect = RectF(
            offsetX + food.x * cellSize + 2,
            offsetY + food.y * cellSize + 2,
            offsetX + (food.x + 1) * cellSize - 2,
            offsetY + (food.y + 1) * cellSize - 2
        )
        canvas.drawOval(rect, foodPaint)
    }
    
    /**
     * 绘制蛇
     */
    private fun drawSnake(canvas: Canvas) {
        for (i in snake.indices) {
            val point = snake[i]
            val rect = RectF(
                offsetX + point.x * cellSize + 1,
                offsetY + point.y * cellSize + 1,
                offsetX + (point.x + 1) * cellSize - 1,
                offsetY + (point.y + 1) * cellSize - 1
            )
            
            // 蛇头用深色，蛇身用浅色
            val paint = if (i == 0) snakeHeadPaint else snakeBodyPaint
            canvas.drawRoundRect(rect, 8f, 8f, paint)
        }
    }
    
    /**
     * 点类
     */
    data class Point(var x: Int, var y: Int)
    
    /**
     * 获取游戏是否结束
     */
    fun isGameOver(): Boolean = isGameOver
    
    /**
     * 获取当前分数
     */
    fun getScore(): Int = score

    /**
     * 设置网格高度
     */
    fun setGridHeight(height: Int) {
        gridHeight = height
        if (width > 0 && height > 0) {
            calculateCellSize(width, height)
        }
    }
}