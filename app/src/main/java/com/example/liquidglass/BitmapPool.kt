package com.example.liquidglass

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class BitmapPool private constructor() {
    
    companion object {
        private const val TAG = "BitmapPool"
        private const val MAX_POOL_SIZE = 20
        private const val ENABLE_LOG = false
        
        @Volatile
        private var instance: BitmapPool? = null
        
        fun getInstance(): BitmapPool {
            return instance ?: synchronized(this) {
                instance ?: BitmapPool().also { instance = it }
            }
        }
    }
    
    private val pool = ConcurrentHashMap<String, MutableList<Bitmap>>()
    private var totalSize = 0
    
    fun get(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val key = makeKey(width, height, config)
        
        synchronized(pool) {
            val list = pool[key]
            if (!list.isNullOrEmpty()) {
                val bitmap = list.removeAt(list.size - 1)
                totalSize--
                bitmap.eraseColor(0)
                return bitmap
            }
        }
        return Bitmap.createBitmap(width, height, config)
    }
    
    fun put(bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.isRecycled) {
            return false
        }
        
        synchronized(pool) {
            if (totalSize >= MAX_POOL_SIZE) {
                bitmap.recycle()
                return false
            }
            
            val key = makeKey(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
            val list = pool.getOrPut(key) { mutableListOf() }
            
            list.add(bitmap)
            totalSize++
            return true
        }
    }
    
    fun clear() {
        synchronized(pool) {
            pool.values.forEach { list ->
                list.forEach { it.recycle() }
                list.clear()
            }
            pool.clear()
            totalSize = 0
        }
    }
    
    fun getStats(): String {
        synchronized(pool) {
            return "BitmapPool: 总数=$totalSize, 类型数=${pool.size}"
        }
    }
    
    private fun makeKey(width: Int, height: Int, config: Bitmap.Config): String {
        return "${width}_${height}_${config.name}"
    }
}
