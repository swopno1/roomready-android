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
      StaffMemberEntity::class,
      OutboxEntry::class,
      SyncStateEntity::class,
    ],
  version = 9,
  exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun roomTypeDao(): RoomTypeDao

  abstract fun roomDao(): RoomDao

  abstract fun checklistRunDao(): ChecklistRunDao

  abstract fun ticketDao(): TicketDao

  abstract fun statusLogDao(): StatusLogDao

  abstract fun staffDao(): StaffDao

  abstract fun outboxDao(): OutboxDao

  abstract fun syncStateDao(): SyncStateDao

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

    // Data-only: RoomStatus.INSPECTED was renamed to CLEANED and statuses
    // persist as enum-name TEXT. Rewrite stored values so valueOf() keeps
    // resolving on existing installs.
    val MIGRATION_3_4: Migration =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("UPDATE rooms SET status = 'CLEANED' WHERE status = 'INSPECTED'")
          db.execSQL("UPDATE status_logs SET fromStatus = 'CLEANED' WHERE fromStatus = 'INSPECTED'")
          db.execSQL("UPDATE status_logs SET toStatus = 'CLEANED' WHERE toStatus = 'INSPECTED'")
        }
      }

    val MIGRATION_4_5: Migration =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE tickets ADD COLUMN description TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE tickets ADD COLUMN resolvedBy TEXT")
          db.execSQL("ALTER TABLE tickets ADD COLUMN resolvedAt INTEGER")
        }
      }

    val MIGRATION_5_6: Migration =
      object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE checklist_runs ADD COLUMN returnReasonKey TEXT")
          db.execSQL("ALTER TABLE checklist_runs ADD COLUMN returnNote TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE status_logs ADD COLUMN reason TEXT")
        }
      }

    val MIGRATION_6_7: Migration =
      object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE checklist_runs ADD COLUMN photoPath TEXT")
          db.execSQL("ALTER TABLE checklist_runs ADD COLUMN photoSkippedReason TEXT")
          db.execSQL("ALTER TABLE tickets ADD COLUMN photoPath TEXT")
        }
      }

    val MIGRATION_7_8: Migration =
      object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE room_types ADD COLUMN pmsTypeId TEXT")
          db.execSQL("ALTER TABLE rooms ADD COLUMN pmsUnitId TEXT")
          db.execSQL("ALTER TABLE rooms ADD COLUMN block TEXT")
          db.execSQL("ALTER TABLE rooms ADD COLUMN departsToday INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE rooms ADD COLUMN pmsUnitStatus TEXT")
          db.execSQL("ALTER TABLE rooms ADD COLUMN pmsHousekeepingAt INTEGER")
          db.execSQL("ALTER TABLE tickets ADD COLUMN pmsTicketId TEXT")
          db.execSQL("ALTER TABLE status_logs ADD COLUMN source TEXT NOT NULL DEFAULT 'local'")
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS staff_members " +
              "(pmsId TEXT NOT NULL, name TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(pmsId))"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS outbox_entries " +
              "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, createdAt INTEGER NOT NULL, " +
              "kind TEXT NOT NULL, unitPmsId TEXT, ticketLocalId INTEGER, payloadJson TEXT NOT NULL, " +
              "attempts INTEGER NOT NULL, lastError TEXT)"
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_entries_createdAt ON outbox_entries(createdAt)")
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_state " +
              "(id INTEGER NOT NULL, propertyName TEXT, propertyTimezone TEXT, businessDate TEXT, " +
              "contractVersion INTEGER, lastSyncAt INTEGER, lastError TEXT, PRIMARY KEY(id))"
          )
        }
      }

    val MIGRATION_8_9: Migration =
      object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
          // Demo Mode is gone: drop the isDemo columns. Table rebuilds
          // (not DROP COLUMN) so legacy SQLite on API 24+ works. DROP
          // TABLE does not fire FK actions, so Live rows survive with
          // ids intact; indices are recreated below.
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS tickets_new " +
              "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, roomId INTEGER NOT NULL, " +
              "roomName TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, " +
              "priority TEXT NOT NULL, status TEXT NOT NULL, createdBy TEXT NOT NULL, " +
              "createdAt INTEGER NOT NULL, resolvedBy TEXT, resolvedAt INTEGER, " +
              "photoPath TEXT, pmsTicketId TEXT, " +
              "FOREIGN KEY(roomId) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
          )
          db.execSQL(
            "INSERT INTO tickets_new (id, roomId, roomName, title, description, priority, " +
              "status, createdBy, createdAt, resolvedBy, resolvedAt, photoPath, pmsTicketId) " +
              "SELECT id, roomId, roomName, title, description, priority, " +
              "status, createdBy, createdAt, resolvedBy, resolvedAt, photoPath, pmsTicketId " +
              "FROM tickets"
          )
          db.execSQL("DROP TABLE tickets")
          db.execSQL("ALTER TABLE tickets_new RENAME TO tickets")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_tickets_roomId ON tickets(roomId)")

          db.execSQL(
            "CREATE TABLE IF NOT EXISTS rooms_new " +
              "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
              "roomTypeId INTEGER NOT NULL, roomTypeName TEXT NOT NULL, floor INTEGER NOT NULL, " +
              "status TEXT NOT NULL, assignedTo TEXT, updatedAt INTEGER NOT NULL, " +
              "notes TEXT NOT NULL, isOccupied INTEGER NOT NULL, pmsUnitId TEXT, block TEXT, " +
              "departsToday INTEGER NOT NULL, pmsUnitStatus TEXT, pmsHousekeepingAt INTEGER, " +
              "FOREIGN KEY(roomTypeId) REFERENCES room_types(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
          )
          db.execSQL(
            "INSERT INTO rooms_new (id, name, roomTypeId, roomTypeName, floor, status, " +
              "assignedTo, updatedAt, notes, isOccupied, pmsUnitId, block, departsToday, " +
              "pmsUnitStatus, pmsHousekeepingAt) " +
              "SELECT id, name, roomTypeId, roomTypeName, floor, status, " +
              "assignedTo, updatedAt, notes, isOccupied, pmsUnitId, block, departsToday, " +
              "pmsUnitStatus, pmsHousekeepingAt FROM rooms"
          )
          db.execSQL("DROP TABLE rooms")
          db.execSQL("ALTER TABLE rooms_new RENAME TO rooms")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_rooms_roomTypeId ON rooms(roomTypeId)")
          db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rooms_name ON rooms(name)")

          db.execSQL(
            "CREATE TABLE IF NOT EXISTS room_types_new " +
              "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
              "checklistTemplateSteps TEXT NOT NULL, pmsTypeId TEXT)"
          )
          db.execSQL(
            "INSERT INTO room_types_new (id, name, checklistTemplateSteps, pmsTypeId) " +
              "SELECT id, name, checklistTemplateSteps, pmsTypeId FROM room_types"
          )
          db.execSQL("DROP TABLE room_types")
          db.execSQL("ALTER TABLE room_types_new RENAME TO room_types")
        }
      }

    fun build(context: Context): AppDatabase =
      Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
        .addMigrations(
          MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
          MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        )
        .build()
  }
}
