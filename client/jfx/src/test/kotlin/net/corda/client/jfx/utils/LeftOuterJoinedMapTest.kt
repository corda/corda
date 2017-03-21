package net.corda.client.jfx.utils

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class LeftOuterJoinedMapTest {

    data class Person(val name: String, val age: Int)
    data class Dog(val name: String, val owner: String)

    lateinit var people: ObservableList<Person>
    lateinit var dogs: ObservableList<Dog>
    lateinit var joinedList: ObservableList<Pair<Person, ObservableList<Dog>>>
    lateinit var replayedList: ObservableList<out Pair<Person, ObservableList<Dog>>>

    @Before
    fun setup() {
        people = FXCollections.observableArrayList<Person>(Person("Alice", 12))
        dogs = FXCollections.observableArrayList<Dog>(Dog("Scruffy", owner = "Bob"))
        joinedList = people.leftOuterJoin(dogs, Person::name, Dog::owner) { person, dogs -> Pair(person, dogs) }
        // We replay the nested observable as well
        replayedList = ReplayedList(joinedList.map { Pair(it.first, ReplayedList(it.second)) })
    }

    // TODO perhaps these are too brittle because they test indices that are not stable. Use Expect dsl?
    @Test
    fun addWorks() {
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0].first.name, "Alice")
        assertEquals(replayedList[0].second.size, 0)

        dogs.add(Dog("Scooby", owner = "Alice"))
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0].first.name, "Alice")
        assertEquals(replayedList[0].second.size, 1)
        assertEquals(replayedList[0].second[0].name, "Scooby")

        people.add(Person("Bob", 34))
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0].first.name, "Alice")
        assertEquals(replayedList[0].second.size, 1)
        assertEquals(replayedList[0].second[0].name, "Scooby")
        assertEquals(replayedList[1].first.name, "Bob")
        assertEquals(replayedList[1].second.size, 1)
        assertEquals(replayedList[1].second[0].name, "Scruffy")

        dogs.add(Dog("Bella", owner = "Bob"))
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0].first.name, "Alice")
        assertEquals(replayedList[0].second.size, 1)
        assertEquals(replayedList[0].second[0].name, "Scooby")
        assertEquals(replayedList[1].first.name, "Bob")
        assertEquals(replayedList[1].second.size, 2)
        assertEquals(replayedList[1].second[0].name, "Bella")
        assertEquals(replayedList[1].second[1].name, "Scruffy")

        // We have another Alice wat
        people.add(Person("Alice", 91))
        assertEquals(replayedList.size, 3)
        assertEquals(replayedList[0].first.name, "Alice")
        assertEquals(replayedList[0].second.size, 1)
        assertEquals(replayedList[0].second[0].name, "Scooby")
        assertEquals(replayedList[1].first.name, "Alice")
        assertEquals(replayedList[1].second.size, 1)
        assertEquals(replayedList[1].second[0].name, "Scooby")
        assertEquals(replayedList[2].first.name, "Bob")
        assertEquals(replayedList[2].second.size, 2)
        assertEquals(replayedList[2].second[0].name, "Bella")
        assertEquals(replayedList[2].second[1].name, "Scruffy")

    }

    @Test
    fun removeWorks() {
        dogs.add(Dog("Scooby", owner = "Alice"))
        people.add(Person("Bob", 34))
        dogs.add(Dog("Bella", owner = "Bob"))

        assertEquals(people.removeAt(0).name, "Alice")
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0].first.name, "Bob")
        assertEquals(replayedList[0].second.size, 2)
        assertEquals(replayedList[0].second[0].name, "Bella")
        assertEquals(replayedList[0].second[1].name, "Scruffy")

        assertEquals(dogs.removeAt(0).name, "Scruffy")
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0].first.name, "Bob")
        assertEquals(replayedList[0].second.size, 1)
        assertEquals(replayedList[0].second[0].name, "Bella")

        people.add(Person("Alice", 213))
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0].first.name, "Alice")
        assertEquals(replayedList[0].second.size, 1)
        assertEquals(replayedList[0].second[0].name, "Scooby")
        assertEquals(replayedList[1].first.name, "Bob")
        assertEquals(replayedList[1].second.size, 1)
        assertEquals(replayedList[1].second[0].name, "Bella")

        dogs.clear()
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0].first.name, "Alice")
        assertEquals(replayedList[0].second.size, 0)
        assertEquals(replayedList[1].first.name, "Bob")
        assertEquals(replayedList[1].second.size, 0)

        people.clear()
        assertEquals(replayedList.size, 0)
    }
}

