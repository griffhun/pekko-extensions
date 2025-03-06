package com.github.griffhun.pekko.example.stream.cluster.singleton.db

import org.testcontainers.containers.PostgreSQLContainer

object PostgresTestContainers {
  val postgresContainer = new PostgreSQLContainer("postgres:16-alpine")
  postgresContainer.start()

  val config = PostgresConfig(
    jdbcUrl = postgresContainer.getJdbcUrl,
    username = postgresContainer.getUsername,
    password = postgresContainer.getPassword
  )
}
