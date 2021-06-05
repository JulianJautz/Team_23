package at.tugraz.onpoint.database

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.tugraz.onpoint.R
import at.tugraz.onpoint.ui.main.ScheduledNotificationReceiver
import java.net.URL
import java.util.*

// https://developer.android.com/reference/android/arch/persistence/room/ColumnInfo
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `assignment` (`title` TEXT NOT NULL, `description` TEXT NOT NULL, `deadline` INTEGER NOT NULL, `links` TEXT NOT NULL, `uid` INTEGER PRIMARY KEY AUTOINCREMENT, `moodle_id` INTEGER)")
        database.execSQL("CREATE TABLE IF NOT EXISTS `moodle` (`universityName` TEXT NOT NULL, `userName` TEXT NOT NULL, `password` TEXT NOT NULL, `apiLink` TEXT NOT NULL, `uid` INTEGER PRIMARY KEY AUTOINCREMENT)")
    }
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `assignment` ADD done INTEGER")
    }
}

val MIGRATION_3_4: Migration = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `assignment` (`title` TEXT NOT NULL, `description` TEXT NOT NULL, `deadline` INTEGER NOT NULL, `links` TEXT NOT NULL, `uid` INTEGER PRIMARY KEY AUTOINCREMENT, `moodle_id` INTEGER, `is_custom` INTEGER NOT NULL)")

    }
}

@Database(
    entities = [Todo::class, Assignment::class, Moodle::class],
    version = 4,
    exportSchema = true
)
abstract class OnPointAppDatabase : RoomDatabase() {
    abstract fun getTodoDao(): TodoDao
    abstract fun getMoodleDao(): MoodleDao
    abstract fun getAssignmentDao(): AssignmentDao
}

@Entity
data class Todo(
    @PrimaryKey(autoGenerate = true)
    val uid: Int = -1,

    @ColumnInfo(name = "title")
    var title: String,

    @ColumnInfo(name = "creation_unix_time")
    val creationUnixTime: Int = -1,

    @ColumnInfo(name = "expiration_unix_time", defaultValue = "NULL")
    var expirationUnixTime: Int? = null,

    @ColumnInfo(name = "is_completed", defaultValue = "0")
    var isCompleted: Boolean = false,
) {
    fun creationDateTime(): Date {
        return Date(creationUnixTime.toLong() * 1000)
    }

    fun expirationDateTime(): Date? {
        if (expirationUnixTime != null) {
            return Date(expirationUnixTime!!.toLong() * 1000)
        }
        return null
    }
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo")
    fun selectAll(): List<Todo>

    @Query("SELECT * FROM todo WHERE is_completed")
    fun selectAllCompleted(): List<Todo>

    @Query("SELECT * FROM todo WHERE NOT is_completed")
    fun selectAllNotCompleted(): List<Todo>

    @Query("SELECT * FROM todo WHERE uid = (:uid)")
    fun selectOne(uid: Long): Todo

    @Query("INSERT INTO todo (title, creation_unix_time, expiration_unix_time, is_completed) VALUES (:title, strftime('%s', 'now'), NULL, 0)")
    fun insertNew(title: String): Long

    @Insert
    fun insertOne(todo: Todo)

    @Update
    fun updateMany(vararg todo: Todo)

    @Update
    fun updateOne(todo: Todo)

    @Delete
    fun deleteOne(todo: Todo)

    @Delete
    fun deleteMany(vararg todo: Todo)

    @Query("DELETE FROM todo")
    fun deleteAll()
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignment ORDER BY deadline ASC")
    fun selectAll(): List<Assignment>

    @Query("SELECT * FROM assignment WHERE NOT is_completed ORDER BY deadline ASC")
    fun selectAllNotCompleted(): List<Assignment>

    @Query("SELECT * FROM assignment WHERE is_completed ORDER BY deadline DESC")
    fun selectAllCompleted(): List<Assignment>

    @Query("SELECT * FROM assignment WHERE uid = (:uid)")
    fun selectOne(uid: Long): Assignment

    @Update
    fun updateOne(assignment: Assignment)

    @Query("INSERT INTO assignment (title, description, deadline, links, moodle_id, is_custom, is_completed) VALUES (:title, :description, :deadline, :links, :moodleId, :isCustom, :isCompleted)")
    fun insertOneRaw(
        title: String,
        description: String,
        deadline: Long,
        links: String,
        moodleId: Int?,
        isCustom: Boolean,
        isCompleted: Boolean,
    ): Long

    fun insertOneFromMoodle(
        title: String,
        description: String,
        deadline: Date,
        links: List<URL>? = null,
        moodleId: Int? = null,
    ): Long {
        return insertOneRaw(
            title,
            description,
            Assignment.convertDeadlineDate(deadline),
            Assignment.encodeLinks(links ?: arrayListOf()),
            moodleId,
            isCustom = false,
            isCompleted = false,
        )
    }

    fun insertOneCustom(
        title: String,
        description: String,
        deadline: Date,
        links: List<URL>? = null,
    ): Long {
        return insertOneRaw(
            title,
            description,
            Assignment.convertDeadlineDate(deadline),
            Assignment.encodeLinks(links ?: arrayListOf()),
            moodleId = null,
            isCustom = true,
            isCompleted = false,
        )
    }

    @Query("DELETE FROM assignment")
    fun deleteAll()

    @Query("DELETE FROM assignment WHERE NOT is_custom")
    fun deleteMoodleAssignments()
}

@Entity
data class Moodle(
    @PrimaryKey(autoGenerate = true)
    val uid: Int = -1,

    @ColumnInfo(name = "universityName")
    var universityName: String,

    @ColumnInfo(name = "userName")
    var userName: String,

    @ColumnInfo(name = "password")
    var password: String,

    @ColumnInfo(name = "apiLink")
    var apiLink: String,
    // TODO unique combo of apilink and username
)

@Dao
interface MoodleDao {
    @Query("SELECT * FROM moodle")
    fun selectAll(): List<Moodle>

    @Query("SELECT * FROM moodle WHERE uid = (:uid)")
    fun selectOne(uid: Long): Moodle

    @Query("INSERT INTO moodle (universityName, userName, password, apiLink) VALUES (:universityName, :userName, :password, :apiLink)")
    fun insertOne(universityName: String, userName: String, password: String, apiLink: String): Long
    // TODO on insert conflict, do nothing
}

var INSTANCE: OnPointAppDatabase? = null

fun getDbInstance(context: Context?): OnPointAppDatabase {
    if (INSTANCE == null) {
        val builder = Room.databaseBuilder(
            context!!,
            OnPointAppDatabase::class.java,
            "OnPointDb_v4"
        )
        // DB queries in the main thread need to be allowed explicitly to avoid a compilation error.
        // By default IO operations should be delegated to a background thread to avoid the UI
        // getting stuck on long IO operations.
        // We have very fast IO operations (small updates) and introducing background threads
        // and async queries is a pain for what we need to achieve.
        builder.allowMainThreadQueries()
        builder.addMigrations(MIGRATION_1_2)
        builder.addMigrations(MIGRATION_2_3)
        builder.addMigrations(MIGRATION_3_4)
        INSTANCE = builder.build()
    }
    return INSTANCE as OnPointAppDatabase
}

@Entity
data class Assignment(
    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "deadline")
    var deadlineUnixTime: Long,

    @ColumnInfo(name = "links")
    var links: String = "",

    @PrimaryKey(autoGenerate = true)
    var uid: Int? = null,

    @ColumnInfo(name = "moodle_id")
    var moodleId: Int? = null,

    @ColumnInfo(name = "is_custom")
    var isCustom: Boolean = false,

    @ColumnInfo(name = "is_completed")
    var isCompleted: Boolean = false
) {
    companion object {
        fun convertDeadlineDate(deadline: Date): Long {
            return deadline.time / 1000
        }

        fun encodeLinks(linksList: List<URL>): String {
            var encoded = ""
            linksList.forEach {
                encoded += "$it;"
            }
            return encoded.trimEnd(';')
        }
    }

    fun getDeadlineDate(): Date {
        return Date(deadlineUnixTime * 1000)
    }

    fun getLinksAsUrls(): List<URL> {
        if (links.isEmpty()) {
            return emptyList()
        }
        return links.split(";").map {
            URL(it)
        }
    }

    fun linksToMultiLineString(): String {
        val text: StringBuilder = StringBuilder()
        getLinksAsUrls().forEach {
            text.append(it)
            text.append('\n')
        }
        return text.toString()
    }

    // Call this function ONLY after the ID is set.
    fun buildAndScheduleNotification(context: Context, reminder_date: Calendar) {
        val intentToLaunchNotification = Intent(context, ScheduledNotificationReceiver::class.java)
        intentToLaunchNotification.putExtra(
            "title",
            context.getString(R.string.assignment_notification_title)
        )
        intentToLaunchNotification.putExtra(
            "text",
            this.title + ": " + this.getDeadlineDate().toString()
        )
        intentToLaunchNotification.putExtra("notificationId", uid)
        // Schedule notification
        val pending = PendingIntent.getBroadcast(
            context,
            uid!!,
            intentToLaunchNotification,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.set(AlarmManager.RTC_WAKEUP, reminder_date.timeInMillis, pending)
    }
}
