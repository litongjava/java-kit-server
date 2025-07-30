package com.litongjava.kit.config;

import java.net.URL;
import java.util.List;

import com.litongjava.db.activerecord.Db;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.hutool.ResourceUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DbTables {

  public static void init() {
    String userTableName = "uni_tts_cache";

    boolean created = createTable(userTableName);
    if (created) {
      log.info("created table:{}", userTableName);
    }

  }

  private static boolean createTable(String userTableName) {
    String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
    List<String> tables = Db.queryListString(sql, userTableName);
    int size = tables.size();
    if (size < 1) {
      URL url = ResourceUtil.getResource("sql/" + userTableName + ".sql");
      String sql2 = FileUtil.readString(url);
      int update = Db.update(sql2);
      log.info("created:{},{}", userTableName, update);
      return true;
    }
    return false;
  }
}