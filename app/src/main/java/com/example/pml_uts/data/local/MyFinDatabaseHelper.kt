package com.example.pml_uts.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.pml_uts.data.model.Menu

class MyFinDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "myfin_offline.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_MENU = "cached_menu"
        const val COL_ID = "id"
        const val COL_KANTIN_ID = "kantin_id"
        const val COL_NAMA_MENU = "nama_menu"
        const val COL_KATEGORI = "kategori"
        const val COL_HARGA = "harga"
        const val COL_STOCK = "stock"
        const val COL_FOTO_MENU = "foto_menu"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createMenuTable = ("CREATE TABLE " + TABLE_MENU + " ("
                + COL_ID + " INTEGER PRIMARY KEY,"
                + COL_KANTIN_ID + " INTEGER,"
                + COL_NAMA_MENU + " TEXT,"
                + COL_KATEGORI + " TEXT,"
                + COL_HARGA + " REAL,"
                + COL_STOCK + " INTEGER,"
                + COL_FOTO_MENU + " TEXT)")
        db.execSQL(createMenuTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MENU)
        onCreate(db)
    }

    fun saveMenuCache(menus: List<Menu>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_MENU, null, null)
            for (menu in menus) {
                val values = ContentValues().apply {
                    put(COL_ID, menu.id)
                    put(COL_KANTIN_ID, menu.kantinId)
                    put(COL_NAMA_MENU, menu.namaMenu)
                    put(COL_KATEGORI, menu.kategori)
                    put(COL_HARGA, menu.harga)
                    put(COL_STOCK, menu.stock)
                    put(COL_FOTO_MENU, menu.fotoMenu)
                }
                db.insert(TABLE_MENU, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun getCachedMenus(): List<Menu> {
        val list = mutableListOf<Menu>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM " + TABLE_MENU, null)
        if (cursor.moveToFirst()) {
            do {
                val menu = Menu(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                    kantinId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_KANTIN_ID)),
                    namaMenu = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAMA_MENU)),
                    kategori = cursor.getString(cursor.getColumnIndexOrThrow(COL_KATEGORI)),
                    harga = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_HARGA)),
                    stock = cursor.getInt(cursor.getColumnIndexOrThrow(COL_STOCK)),
                    fotoMenu = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOTO_MENU))
                )
                list.add(menu)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }
}