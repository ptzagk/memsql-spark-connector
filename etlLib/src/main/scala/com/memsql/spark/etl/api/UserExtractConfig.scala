package com.memsql.spark.etl.api

import spray.json.JsValue

case class UserExtractConfig(class_name: String, value: JsValue) extends PhaseConfig with UserConfig
