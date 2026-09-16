/**
 * 示例场景脚本：走契约全链路（解包 / traceId / 401 刷新重放 / 错误三分类）。
 * 挂载到场景中的一个节点上，运行时自动拉取列表并展示。
 *
 * Cocos Creator 3.x：import { _decorator, Component, Label } from 'cc';
 * 此文件为 TS 模板，在编辑器内编译运行。
 */
import { _decorator, Component, Label } from "cc";
import { api } from "./ApiManager";
import type { ApiError, PageData } from "@yarch/contract/core";

const { ccclass, property } = _decorator;

interface PlayerScore {
  id: number;
  name: string;
  score: number;
}

@ccclass("MainScene")
export class MainScene extends Component {
  @property(Label)
  statusLabel: Label | null = null;

  @property(Label)
  resultLabel: Label | null = null;

  async start() {
    this.setStatus("加载中…");
    try {
      const page = await api.get<PageData<PlayerScore>>("/api/v1/scores?page=1");
      const list = page.list;
      this.setResult(`加载成功：${list.length} 条记录`);
      if (list.length > 0) {
        this.setResult(`${this.getResultText()}\n榜首：${list[0].name} - ${list[0].score}`);
      }
    } catch (e) {
      const err = e as ApiError;
      if (err.code === -1) {
        // 传输错误：统一网络类文案（client-shared 一-4 传输分类）
        this.setStatus("网络异常，请检查连接");
      } else {
        // 业务错误：服务端 message 可展示（client-shared 一-4 业务分类）
        this.setStatus(`错误：${err.message}`);
      }
    }
  }

  private setStatus(text: string) {
    if (this.statusLabel) this.statusLabel.string = text;
  }

  private setResult(text: string) {
    if (this.resultLabel) this.resultLabel.string = text;
  }

  private getResultText(): string {
    return this.resultLabel?.string ?? "";
  }
}
