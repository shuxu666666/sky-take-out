# sky-take-out 项目注解汇总

> 扫描范围：`sky-server` / `sky-common` / `sky-pojo` 全部 Java 源文件
> 统计口径：包含注释中的出现（如 `@Scheduled` 有 3 处被注释掉，表中会标注）
> 文件位置：`docs/annotations.md`（项目根目录，不参与构建）
> 生成日期：2026-10-27

---

## 目录

1. [启动与全局配置](#一启动与全局配置)
2. [Web 层：控制器与路由](#二web-层控制器与路由)
3. [参数绑定](#三参数绑定)
4. [依赖注入与组件注册](#四依赖注入与组件注册)
5. [持久层（MyBatis 注解 SQL）](#五持久层mybatis-注解-sql)
6. [事务、缓存、定时、WebSocket](#六事务缓存定时websocket)
7. [AOP 与自定义注解](#七aop-与自定义注解)
8. [Lombok](#八lombok)
9. [Swagger / Knife4j](#九swagger--knife4j)
10. [测试](#十测试)
11. [附：`@RequestMapping` 家族关系](#附requestmapping-家族关系)
12. [附：哪个注解管哪层](#附哪个注解管哪层)

---

## 一、启动与全局配置

| 注解 | 次数 | 含义 | 项目位置 |
|---|---|---|---|
| `@SpringBootApplication` | 1 | 启动类三合一：`@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`。标在 main 方法所在类上，扫描**本包及子包** | `SkyApplication.java:10` |
| `@EnableTransactionManagement` | 1 | 开启基于 `@Transactional` 的**注解式事务** | `SkyApplication.java:11` |
| `@EnableCaching` | 1 | 开启 Spring 缓存抽象，让 `@Cacheable` / `@CacheEvict` 生效 | `SkyApplication.java:13` |
| `@EnableScheduling` | 1 | 开启定时任务，让 `@Scheduled` 生效 | `SkyApplication.java:14` |
| `@Configuration` | 4 | 声明"这是一个配置类"，里面可以写 `@Bean`。本质是 `@Component` 的变体 | `WebMvcConfiguration`、`OssConfiguration`、`RedisConfiguration`、`WebSocketConfiguration` |
| `@Bean` | 5 | 标在方法上，把方法返回值注册成 Spring 容器里的 Bean。**方法名就是 Bean 名** | `WebMvcConfiguration:60`（Knife4j）、`:77`（拦截器注册）、`RedisConfiguration:12`（`RedisTemplate`） |
| `@ConfigurationProperties(prefix = "sky.xxx")` | 3 | 把 `application.yml` 里 `sky.xxx.*` 的配置**批量绑定**到字段上，省掉一个个 `@Value` | `JwtProperties`、`WeChatProperties`、`AliOssProperties` |

> 前端类比：`@ConfigurationProperties` ≈ 把 `config.json` 的一个嵌套节点反序列化成一个 TS interface 实例。

---

## 二、Web 层：控制器与路由

| 注解 | 次数 | 含义 | 例子 |
|---|---|---|---|
| `@RestController` | 18 | `@Controller` + `@ResponseBody`。方法返回值**直接序列化成 JSON 写进响应体**，不再找视图 | 所有 `admin/*Controller`、`user/*Controller` |
| `@RequestMapping("/admin/dish")` | 19 | 类级别**公共路径前缀**；也可指定 `method` | `DishController:22` |
| `@GetMapping` | 34 | 对应 HTTP GET，**查询**语义 | `ReportController:90` `/export` |
| `@PostMapping` | 17 | 对应 HTTP POST，**新增/提交**语义 | `DishController` 的 `save` |
| `@PutMapping` | 14 | 对应 HTTP PUT，**修改**语义 | `DishController` 的 `update` |
| `@DeleteMapping` | 5 | 对应 HTTP DELETE，**删除**语义 | `DishController` 的 `delete` |
| `@RestControllerAdvice` | 1 | 全局增强：作用于**所有** Controller，常配合 `@ExceptionHandler` 做统一异常处理 | `GlobalExceptionHandler:14` |
| `@ExceptionHandler` | 2 | 标注方法处理指定异常类型，返回统一 `Result` 给前端 | `GlobalExceptionHandler:24`（`BaseException`）、`:35`（兜底 `Exception`） |

> `@RestControllerAdvice` 前端类比：Express 里的全局 `app.use((err, req, res, next) => {...})` 错误中间件。

---

## 三、参数绑定

| 注解 | 次数 | 从哪取值 | 适用场景 | 例子 |
|---|---|---|---|---|
| `@PathVariable` | 17 | **URL 路径段** `/{id}` | RESTful 风格资源定位 | `DishController` 的 `@PathVariable Long id` |
| `@RequestParam` | 7 | **query string / form** `?a=1&b=2` | 少量简单参数 | `SetmealController:42` 的 `ids` |
| `@RequestBody` | 20 | **请求体 JSON** | 复杂对象（DTO） | `DishController` 的 `@RequestBody DishDTO` |
| `@ModelAttribute` | 1 | **query string 逐字段绑定到对象** | 对象字段多、但都是简单类型 | `SetmealController:59` `pageQuery(@ModelAttribute SetmealPageQueryDTO)` |
| `@DateTimeFormat(pattern = ...)` | 10 | 把 query 里的字符串**转成** `LocalDate` / `LocalDateTime` | GET 请求传日期 | `ReportController:40` |
| `@JsonFormat(shape = STRING, pattern = ...)` | 3（1 处生效） | 请求体 JSON ↔ 时间类型互转 | POST 请求传日期 | `OrdersSubmitDTO:19` |

### 核心规则

```java
// 1. 无注解 + 简单类型 → 默认当 @RequestParam
public Result<?> f(LocalDate begin)                        // 等价 @RequestParam

// 2. 无注解 + 复杂对象 → 默认当 @ModelAttribute（逐字段绑定 query）
public Result<?> f(SetmealPageQueryDTO dto)

// 3. Servlet 内置对象 → 按类型自动注入，不能加注解
public void export(HttpServletResponse response)

// 4. 路径变量 / JSON 体 → 必须显式注解
public Result<?> g(@PathVariable Long id)
public Result<?> h(@RequestBody DishDTO dto)
```

### `@DateTimeFormat` vs `@JsonFormat`

| | 处理的数据来源 | 用在哪 |
|---|---|---|
| `@DateTimeFormat` | query / form（Spring 的 `Converter`） | GET 的 `@RequestParam` |
| `@JsonFormat` | 请求体 JSON（Jackson 的 `JsonDeserializer`） | POST 的 `@RequestBody` / 响应 JSON |

---

## 四、依赖注入与组件注册

| 注解 | 次数 | 含义 | 例子 |
|---|---|---|---|
| `@Autowired` | 61 | 按类型自动注入依赖。可标字段、构造器、方法 | 各 Controller 的 `@Autowired private XxxService` |
| `@Service` | 10 | 标在**业务层**实现类上，注册为 Bean（本质 `@Component`） | `ReportServiceImpl` |
| `@Component` | 11 | **通用** Bean 声明，语义最弱 | `AutoFillAspect`、`WebSocketServer`、`TestScheduling` |
| `@Mapper` | 11 | 告诉 MyBatis "这是 Mapper 接口"，由 MyBatis 生成代理实现类 | 所有 `mapper/*` 接口 |
| `@Param` | 4 | 给 Mapper 方法参数**起名**，供 `#{}` 引用。多参数时必须加，否则只能用 `arg0` / `param1` | `OrderMapper:41` `getByIdAndUserId(@Param("id")...)` |

> `@Service` / `@Component` / `@Configuration` / `@RestController` 本质都是 `@Component` 的特化，**只是语义标签**，Spring 扫描时一视同仁。区分它们是为了让人读懂代码分层。

---

## 五、持久层（MyBatis 注解 SQL）

| 注解 | 次数 | 含义 | 例子 |
|---|---|---|---|
| `@Select("select ...")` | 20 | 直接在接口方法上写 SQL，不用 XML | `AddressBookMapper:33` |
| `@Insert("insert ...")` | 4 | 同上，INSERT | `CategoryMapper:20` |
| `@Update("update ...")` | 2 | 同上，UPDATE | `AddressBookMapper:46` |
| `@Delete("delete ...")` | 8 | 同上，DELETE | `AddressBookMapper:53` |

> 项目里**两种方式并存**：简单 SQL 用注解（如 `AddressBookMapper`），复杂动态 SQL 用 XML（`DishMapper.xml`、`SetmealMapper.xml`、`OrderMapper.xml`）。
> 规则：**一旦需要 `<if>` / `<foreach>`，就搬到 XML。**

---

## 六、事务、缓存、定时、WebSocket

| 注解 | 次数 | 含义 | 项目位置 |
|---|---|---|---|
| `@Transactional` | 8 | **声明式事务**：方法内多条 SQL 要么全成功、要么全回滚。默认只对 `RuntimeException` 回滚 | `DishServiceImpl:43/84/142`、`SetmealServiceImp:44/98/118`、`OrderServiceImpl:63`、`AddressBookServiceImpl:65` |
| `@Cacheable(cacheNames, key)` | 1 | **查缓存**：命中直接返回，不执行方法体 | `user/SetmealController:34` |
| `@CacheEvict(cacheNames, key / allEntries)` | 4 | **清缓存**：写操作后失效缓存，避免脏数据 | `admin/SetmealController:32/42/68/84` |
| `@Scheduled(cron = "...")` | 5（**2 处生效**） | 定时执行。`cron` 六位：秒 分 时 日 月 周 | 生效：`TestScheduling:16`、`WebSocketTask:18`；被注释：`OrderTask:22/42`、`TestScheduling:11` |
| `@ServerEndpoint("/ws/{sid}")` | 1 | JSR-356 标准 WebSocket 端点，浏览器可通过 `ws://host/ws/{sid}` 连接 | `WebSocketServer:18` |
| `@OnOpen` | 1 | 连接建立时回调 | `WebSocketServer` |
| `@OnMessage` | 1 | 收到客户端消息时回调 | `WebSocketServer` |
| `@OnClose` | 1 | 连接关闭时回调 | `WebSocketServer` |
| `@PathParam("sid")` | 3 | **WebSocket 专用**的路径变量，取 `{sid}`。注意和 Spring 的 `@PathVariable` 不是一回事 | `WebSocketServer` |

> ⚠️ `@PathParam` 易混点：名字极像 `@PathVariable`，但它在 `javax.websocket.server` 包里，只在 `@ServerEndpoint` 方法中有效。

---

## 七、AOP 与自定义注解

| 注解 | 次数 | 含义 | 项目位置 |
|---|---|---|---|
| `@Aspect` | 1 | 声明切面类，配合 `@Component` 注册 | `AutoFillAspect:20` |
| `@Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(...)")` | 1 | 定义**切点**：拦截 `mapper` 包下所有方法，且带 `@AutoFill` 注解 | `AutoFillAspect:28` |
| `@Before("autoFillPointCut()")` | 1 | **前置通知**：目标方法执行前先跑，用于填充 `createTime/updateTime/createUser/updateUser` | `AutoFillAspect:34` |
| `@Target(ElementType.METHOD)` | 1 | **元注解**：自定义注解只能标在方法上 | `AutoFill.java:12` |
| `@Retention(RetentionPolicy.RUNTIME)` | 1 | **元注解**：保留到运行时，AOP 才能通过反射读到它 | `AutoFill.java:13` |
| `@interface AutoFill` | 1 | 定义注解本身 | `AutoFill.java:14` |
| `@AutoFill(OperationType.INSERT / UPDATE)` | 8 | **项目自定义注解**，标记该方法需要自动填充公共字段 | `DishMapper`、`SetmealMapper` 等 |

### 完整链路

```
@AutoFill（标记）
     ↓
@Pointcut（拦截带该注解的 mapper 方法）
     ↓
@Before（切面用反射读取实体字段并填值）
     ↓
Mapper 执行 SQL
```

> 前端类比：自定义装饰器 `@Log()` + 拦截器统一处理。

---

## 八、Lombok

| 注解 | 次数 | 生成什么 | 例子 |
|---|---|---|---|
| `@Data` | 55 | getter / setter / `toString` / `equals` / `hashCode` | 几乎所有 entity、DTO、VO |
| `@Builder` | 29 | **建造者模式**：`Xxx.builder().a(1).b(2).build()` | `ReportServiceImpl:240` 构造 VO |
| `@NoArgsConstructor` | 31 | 无参构造。**MyBatis 反序列化需要** | 配合 `@Builder` 时几乎必加 |
| `@AllArgsConstructor` | 32 | 全参构造，`@Builder` 依赖它 | 同上 |
| `@Slf4j` | 32 | 生成 `private static final Logger log`，可直接 `log.info(...)` | 所有 Service、Aspect、启动类 |

> ⚠️ `@Builder` 会**干掉默认无参构造**，所以常写成 `@Data @Builder @NoArgsConstructor @AllArgsConstructor` 四件套。项目里大量实体都是这个组合。

---

## 九、Swagger / Knife4j

| 注解 | 次数 | 含义 | 例子 |
|---|---|---|---|
| `@Api(tags = "菜品相关接口")` | 17 | 类级别，给这组接口起个**分组名** | `DishController:25` |
| `@ApiOperation("数据导出")` | 70 | 方法级别，**接口说明**（文档上显示的名字） | `ReportController:91` |
| `@ApiModel(description = "...")` | 2 | 模型类说明 | `EmployeeLoginDTO:10` |
| `@ApiModelProperty("用户名")` | 6 | 字段说明，文档表格里的"参数说明"列 | `EmployeeLoginDTO:13` |

> 访问 `http://localhost:8080/doc.html` 可看 Knife4j 生成的文档。

---

## 十、测试

| 注解 | 次数 | 含义 |
|---|---|---|
| `@SpringBootTest` | 2（**均被注释**） | 启动完整 Spring 容器做集成测试 |
| `@Test` | 2（1 处生效） | JUnit 测试方法标记 |

> ⚠️ 项目里 `POITest` 和 `SpringDataRedisTest` 的 `@SpringBootTest` 都是 `//@SpringBootTest`，等于没启用。
> 这也是 `POITest.main()` 能直接跑的原因——它就是个普通类，没有 Spring 上下文。

---

## 附：`@RequestMapping` 家族关系

```java
@RequestMapping(method = RequestMethod.GET)     ≡  @GetMapping
@RequestMapping(method = RequestMethod.POST)    ≡  @PostMapping
@RequestMapping(method = RequestMethod.PUT)     ≡  @PutMapping
@RequestMapping(method = RequestMethod.DELETE)  ≡  @DeleteMapping
```

`@GetMapping` 等只是**限定 method 的 `@RequestMapping` 缩写**。

---

## 附：哪个注解管哪层

| 层 | 核心注解 | 记忆点 |
|---|---|---|
| **启动** | `@SpringBootApplication` + 3 个 `@Enable*` | 干净类上堆开关 |
| **路由** | `@RestController` + `@XxxMapping` | 管 URL |
| **入参** | `@RequestBody` / `@RequestParam` / `@PathVariable` / `@ModelAttribute` | 管"数据从哪来" |
| **业务** | `@Service` + `@Transactional` + `@Cacheable` / `@CacheEvict` | 管逻辑与副作用 |
| **持久** | `@Mapper` + `@Select` / `@Insert` / `@Update` / `@Delete` + `@Param` | 管 SQL |
| **切面** | `@Aspect` + `@Pointcut` + `@Before` + 自定义注解 | 管横切关注点 |
| **数据类** | `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` | 管样板代码 |
| **文档** | `@Api` + `@ApiOperation` + `@ApiModel` / `@ApiModelProperty` | 管给人看的说明 |

---

## 备注

本文件位于项目根部的 `docs/` 目录，**不在 `src/main/resources` 下**，因此不会被 Maven 复制到 `target/classes`，也不会被打进 jar —— 它纯粹是开发期阅读的文档。
