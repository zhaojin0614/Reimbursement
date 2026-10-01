package com.zhaojin.reimbursement.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for locally storing bills.
 */
@Database(
    entities = [BillEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun billDao(): BillDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migrate from v1 to v2:
         * 移除「分类」功能：重建 bills 表去掉 category 列（保留全部既有账单）。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE bills_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount REAL NOT NULL,
                        title TEXT NOT NULL,
                        isIncome INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO bills_new (id, amount, title, isIncome, timestamp)
                    SELECT id, amount, title, isIncome, timestamp FROM bills
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE bills")
                db.execSQL("ALTER TABLE bills_new RENAME TO bills")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_timestamp ON bills(timestamp)")
            }
        }

        /**
         * Migrate from v2 to v3:
         * 账单图片：新增 photoPath 列（存 bill_photos/ 下文件名，null = 无图片）。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN photoPath TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reimbursement_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
