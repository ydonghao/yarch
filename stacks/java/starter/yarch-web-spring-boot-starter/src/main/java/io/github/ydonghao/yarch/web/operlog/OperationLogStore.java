package io.github.ydonghao.yarch.web.operlog;

/** 存储 SPI：默认仅 ndjson 落盘；业务工程实现本接口入库（操作日志查询界面归业务） */
public interface OperationLogStore {

    void save(OperationLogRecord record);
}
