package com.github.griffhun.pekko.example.stream.cluster.singleton.db

final case class StreamOffset(streamId: String, offset: Long)
