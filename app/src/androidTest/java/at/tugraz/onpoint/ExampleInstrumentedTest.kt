package at.tugraz.onpoint

import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class AssignmentsListInstrumentedTest {
    @get:Rule
     var activityRule: ActivityScenarioRule<AssignmentsListActivity> =
        ActivityScenarioRule(AssignmentsListActivity::class.java)

    @Test
    fun assignmentsListExists() {
        launchActivity<AssignmentsListActivity>()
        onView(withId(R.id.assignmentsList)).check(matches(isEnabled()))
    }

    @Test
    fun activityHasHeader() {
        launchActivity<AssignmentsListActivity>()
        onView(withId(R.id.AppBar)).check(matches(isEnabled()))
    }

    @Test
    fun headerHasAllButtons() {
        launchActivity<AssignmentsListActivity>()
        onView(withId(R.id.MainBarButton)).check(matches(isEnabled()))
        onView(withId(R.id.ToDoBarButton)).check(matches(isEnabled()))
        onView(withId(R.id.KurseBarButton)).check(matches(isEnabled()))
    }


}
