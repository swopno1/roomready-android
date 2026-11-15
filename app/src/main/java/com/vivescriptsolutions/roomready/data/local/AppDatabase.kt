package com.vivescriptsolutions.roomready.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities =
    [
      RoomTypeEntity::class,
      RoomEntity::class,
      ChecklistRunEntity::class,
      TicketEntity::class,
      StatusLogEntity::class,
    ],
  version = 3,
  exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun roomTypeDao(): RoomTypeDao

  abstract fun roomDao(): RoomDao

  abstract fun checklistRunDao(): ChecklistRunDao

  abstract fun ticketDao(): TicketDao

  abstract fun statusLogDao(): StatusLogDao

  companion object {
    const val DATABASE_NAME = "roomready.db"

    val MIGRATION_1_2: Migration =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE rooms ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_status_logs_roomId_changedAt ON status_logs(roomId, changedAt)"
          )
        }
      }

    val MIGRATION_2_3: Migration =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE rooms ADD COLUMN isOccupied INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE checklist_runs ADD COLUMN runKind TEXT NOT NULL DEFAULT 'FULL'")
        }
      }

    fun build(context: Context): AppDatabase =
      Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
  }
}
