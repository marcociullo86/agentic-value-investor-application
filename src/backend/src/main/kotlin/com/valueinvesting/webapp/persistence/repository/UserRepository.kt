package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {

    // Case-insensitive lookup mirrors the partial UNIQUE INDEX on LOWER(email)
    // in V001 (TSK-001). Email is always stored as entered (preserving casing),
    // but treated as a normalized identifier for login/registration.
    // [^src: design_&_architecture/data/er-diagram.md §users]
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    fun findByEmailIgnoreCase(@Param("email") email: String): User?
}
