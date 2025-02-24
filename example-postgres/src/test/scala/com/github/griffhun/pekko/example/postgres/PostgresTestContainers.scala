package com.github.griffhun.pekko.example.postgres

import org.testcontainers.containers.PostgreSQLContainer
import slick.jdbc.JdbcBackend

object PostgresTestContainers {
  val postgresContainer = new PostgreSQLContainer("postgres:16-alpine")
  postgresContainer.start()

  val config = PostgresConfig(
    jdbcUrl = postgresContainer.getJdbcUrl,
    username = postgresContainer.getUsername,
    password = postgresContainer.getPassword
  )
}

final case class PostgresConfig(jdbcUrl: String, username: String, password: String) {
  lazy val db: JdbcBackend.Database = JdbcBackend
    .Database
    .forURL(
      url = jdbcUrl,
      user = username,
      password = password,
      driver = "org.postgresql.Driver"
    )
}
