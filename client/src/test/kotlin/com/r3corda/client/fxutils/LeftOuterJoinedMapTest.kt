package com.r3corda.client.fxutils

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.junit.Before
import org.junit.Test
import java.util.*

class LeftOuterJoinedMapTest {

    data class Person(val name: String, val age: Int)
    data class Dog(val name: String, val owner: String)

    var people = FXCollections.observableArrayList<Person>(Person("Alice", 12))
    var dogs = FXCollections.observableArrayList<Dog>(Dog("Scruffy", owner = "Bob"))
    var joinedList = people.leftOuterJoin(dogs, Person::name, Dog::owner)
    // We replay the nested observable as well
    var replayedList = ReplayedList(joinedList.map { Pair(it.first, ReplayedList(it.second))  })

    @Before
    fun setup() {
        people = FXCollections.observableArrayList<Person>(Person("Alice", 12))
        dogs = FXCollections.observableArrayList<Dog>(Dog("Scruffy", owner = "Bob"))
        joinedList = people.leftOuterJoin(dogs, Person::name, Dog::owner)
        replayedList = ReplayedList(joinedList.map { Pair(it.first, ReplayedList(it.second))  })
    }

    // TODO perhaps these are too brittle because they test indices that are not stable. Use Expect dsl?
    @Test
    fun addWorks() {
        require(replayedList.size == 1)
        require(replayedList[0].first.name == "Alice")
        require(replayedList[0].second.size == 0)

        dogs.add(Dog("Scooby", owner = "Alice"))
        require(replayedList.size == 1)
        require(replayedList[0].first.name == "Alice")
        require(replayedList[0].second.size == 1)
        require(replayedList[0].second[0].name == "Scooby")

        people.add(Person("Bob", 34))
        require(replayedList.size == 2)
        require(replayedList[0].first.name == "Alice")
        require(replayedList[0].second.size == 1)
        require(replayedList[0].second[0].name == "Scooby")
        require(replayedList[1].first.name == "Bob")
        require(replayedList[1].second.size == 1)
        require(replayedList[1].second[0].name == "Scruffy")

        dogs.add(Dog("Bella", owner = "Bob"))
        require(replayedList.size == 2)
        require(replayedList[0].first.name == "Alice")
        require(replayedList[0].second.size == 1)
        require(replayedList[0].second[0].name == "Scooby")
        require(replayedList[1].first.name == "Bob")
        require(replayedList[1].second.size == 2)
        require(replayedList[1].second[0].name == "Bella")
        require(replayedList[1].second[1].name == "Scruffy")

        // We have another Alice wat
        people.add(Person("Alice", 91))
        require(replayedList.size == 3)
        require(replayedList[0].first.name == "Alice")
        require(replayedList[0].second.size == 1)
        require(replayedList[0].second[0].name == "Scooby")
        require(replayedList[1].first.name == "Alice")
        require(replayedList[1].second.size == 1)
        require(replayedList[1].second[0].name == "Scooby")
        require(replayedList[2].first.name == "Bob")
        require(replayedList[2].second.size == 2)
        require(replayedList[2].second[0].name == "Bella")
        require(replayedList[2].second[1].name == "Scruffy")

    }

    @Test
    fun removeWorks() {
        dogs.add(Dog("Scooby", owner = "Alice"))
        people.add(Person("Bob", 34))
        dogs.add(Dog("Bella", owner = "Bob"))

        require(people.removeAt(0).name == "Alice")
        require(replayedList.size == 1)
        require(replayedList[0].first.name == "Bob")
        require(replayedList[0].second.size == 2)
        require(replayedList[0].second[0].name == "Bella")
        require(replayedList[0].second[1].name == "Scruffy")

        require(dogs.removeAt(0).name == "Scruffy")
        require(replayedList.size == 1)
        require(replayedList[0].first.name == "Bob")
        require(replayedList[0].second.size == 1)
        require(replayedList[0].second[0].name == "Bella")

        people.add(Person("Alice", 213))
        require(replayedList.size == 2)
        require(replayedList[0].first.name == "Alice")
        require(replayedList[0].second.size == 1)
        require(replayedList[0].second[0].name == "Scooby")
        require(replayedList[1].first.name == "Bob")
        require(replayedList[1].second.size == 1)
        require(replayedList[1].second[0].name == "Bella")

        dogs.clear()
        require(replayedList.size == 2)
        require(replayedList[0].first.name == "Alice")
        require(replayedList[0].second.size == 0)
        require(replayedList[1].first.name == "Bob")
        require(replayedList[1].second.size == 0)

        people.clear()
        require(replayedList.size == 0)
    }
}

