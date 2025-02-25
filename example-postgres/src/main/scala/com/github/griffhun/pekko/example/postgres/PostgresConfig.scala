package com.github.griffhun.pekko.example.postgres

import slick.jdbc.JdbcBackend

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
