# 接口文档 v0.1

基础地址 `http://127.0.0.1:8080`，JSON 使用 UTF-8。成功响应直接返回资源、数组或分页对象；创建返回 201，删除/归档/恢复成功返回 204。

## 系统与菜单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /api/health | 存活状态 |
| GET | /api/me | 当前默认用户及单人模式 |
| GET / POST | /api/canteens | 食堂列表 / 新增 |
| GET / PUT / DELETE | /api/canteens/{id} | 查询 / 修改 / 归档 |
| POST | /api/canteens/{id}/restore | 恢复食堂 |
| GET / POST | /api/stalls | 档口列表 / 新增 |
| GET / PUT / DELETE | /api/stalls/{id} | 查询 / 修改 / 归档 |
| POST | /api/stalls/{id}/restore | 恢复档口 |
| GET / POST | /api/dishes | 菜品分页 / 新增 |
| GET / PUT / DELETE | /api/dishes/{id} | 查询 / 修改 / 归档 |
| POST | /api/dishes/{id}/restore | 恢复菜品 |
| PUT | /api/dishes/{id}/marks | 设置收藏和踩雷 |

食堂列表支持 `includeArchived=true`；档口列表支持 `canteenId`、`includeArchived`。
菜品中的 `active` 表示菜品自身状态，`available` 同时考虑食堂和档口状态。`score=null` 表示尚无评分，`photoUrl=null` 表示暂无照片。

新增和修改食堂：
```json
{"name":"第二食堂","location":"校园西侧"}
```

新增和修改档口：
```json
{"canteenId":1,"name":"面食档口"}
```

新增和修改菜品（PUT 需提交完整字段）：
```json
{"stallId":1,"name":"番茄牛腩饭","price":15.00,"category":"米饭","flavors":["不辣","酸甜"]}
```

修改标记（两个字段均必填，不能同时为 true）：
```json
{"favorite":true,"avoided":false}
```

## 筛选与推荐

`GET /api/dishes` 和 `GET /api/recommendations/random` 支持相同筛选字段：

| 参数 | 类型 | 含义 |
| --- | --- | --- |
| canteenId / stallId | 正整数 | 指定食堂 / 档口 |
| keyword | 字符串 | 菜名、档口名、食堂名包含关键词；按字面量查询 |
| category | 字符串 | 类别精确匹配 |
| flavor | 字符串 | 一个口味标签精确匹配 |
| minPrice / maxPrice | 金额 | 参考价格区间，包含上下界 |
| favorite / avoided | 布尔 | 是否收藏 / 是否踩雷；不传则不限制 |
| eaten | 布尔 | 是否存在吃过记录 |
| includeArchived | 布尔 | 列表是否包含已归档数据，默认 false |
| page / size | 正整数 | 列表分页；默认 1 / 20，size 最大 100 |

所有已传条件以 AND 组合。推荐忽略分页参数，并始终排除不可用及踩雷菜品；即使传 `includeArchived=true` 或 `avoided=true` 也不会突破排除规则。

```text
GET /api/dishes?maxPrice=15&flavor=不辣&size=10
GET /api/dishes?favorite=true
GET /api/dishes?avoided=true
GET /api/dishes?eaten=true
GET /api/recommendations/random?maxPrice=15
```

实际 URL 的中文参数需要编码，演示脚本会处理。分页响应结构：
```json
{"items":[],"page":1,"size":20,"total":0}
```

推荐响应为 `{"dish":{...},"candidateCount":2,"reason":"..."}`。无候选返回 404，业务码 `NO_CANDIDATE`。

## 记录一餐与评分

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/meals | 创建一餐 |
| GET | /api/meals | 按 month、dishId、page、size 查询记录 |
| GET | /api/meals/{id} | 一餐详情 |
| PUT | /api/meals/{id} | 完整替换一餐及其明细、评分 |
| DELETE | /api/meals/{id} | 删除一餐，重新反映在榜单与统计中 |

```json
{
  "eatenAt":"2026-11-23T12:00:00",
  "mealType":"LUNCH",
  "note":"今天和同学一起吃",
  "items":[
    {
      "dishId":1,
      "quantity":2,
      "paidAmount":28.00,
      "rating":{"taste":5,"valueScore":4,"portion":5,"recommendation":5},
      "comment":"牛肉很软"
    }
  ]
}
```

- `mealType` 为 BREAKFAST / LUNCH / DINNER / SNACK。
- `eatenAt` 不得在未来，上面的日期仅示例；按本机校园时间填写。
- `note` 和 `comment` 必填但可为空字符串。
- 同一餐 1～30 种菜，每种菜 1～100 份，不得重复 dishId。
- `paidAmount` 为该菜全部份数实付总额，金额范围 0～999999.99。
- `rating` 可不传或为 null；传入时四项均须为 1～5 整数。
- 用餐响应含历史 `dishName`、`totalAmount` 和菜品明细。
- PUT 为整餐替换，不是局部补丁。历史中原有的已归档菜品仍可保留，但不能向其中添加新的已归档菜品。
- 新增没有幂等去重；相同请求提交两次会记录两餐。

## 照片

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/dishes/{id}/photos | multipart/form-data，文件字段名 file |
| GET | /api/dishes/{id}/photos | 菜品的照片列表，新图在前 |
| GET | /api/photos/{id}/content | 获取二进制图片 |
| DELETE | /api/photos/{id} | 删除照片记录与文件 |

支持 JPEG/PNG，最多 5MB 和 1200 万像素，服务端检查真实格式及能否解码。文件名由服务端生成。菜品封面使用最新上传的照片，删掉封面后自动使用上一张。

```powershell
curl.exe -F "file=@F:\photos\lunch.jpg" http://127.0.0.1:8080/api/dishes/1/photos
```

## 榜单与消费

```text
GET /api/rankings?metric=OVERALL&limit=10
GET /api/statistics/monthly?month=2026-11
```

榜单 `metric`：OVERALL、TASTE、VALUE、PORTION、RECOMMENDATION；limit 为 1～100。结果字段为 rank、dishId、name、score、ratingCount、photoUrl。历史已评分菜品归档后仍保留在个人榜单。

月度结果字段为 month、totalAmount、mealCount、averagePerMeal、days。
days 包含该月的每一天，每天有 date、amount、mealCount，无消费日补 0。月份使用半开时间区间 `[当月首日00:00, 下月首日00:00)`，不把下一月零点计入本月。

## 错误响应

```json
{"code":"INVALID_ARGUMENT","message":"最低价格不能大于最高价格","timestamp":"2026-11-23T15:00:00","errorId":null}
```

| HTTP 状态 | 常见 code | 含义 |
| --- | --- | --- |
| 400 | INVALID_ARGUMENT / VALIDATION_FAILED | 参数、JSON、评分、金额等不合法 |
| 404 | NOT_FOUND / NO_CANDIDATE | 无资源、资源不属于当前用户、无推荐候选 |
| 405 | METHOD_NOT_ALLOWED | 请求方法错误 |
| 409 | DATA_CONFLICT | 同一位置重复名称或数据关联冲突 |
| 413 | FILE_TOO_LARGE | 上传请求超过大小限制 |
| 415 | UNSUPPORTED_MEDIA_TYPE | Content-Type 不支持 |
| 500 | INTERNAL_ERROR | 服务内部错误，通过 errorId 关联日志 |

本地单人版没有 token 和用户切换接口。后续接前端时可使用开发服务器代理，将 `/api` 转发到本服务；当前未开放任意来源的跨域访问。
