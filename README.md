

docker build -t database-isolation-demo .


docker run -d -p 5432:5432 --name my-isolation-test -e POSTGRES_USER=youruser -e POSTGRES_PASSWORD=yourpassword -e POSTGRES_DB=yourdb  postgres:15-alpine
docker run --network=container:my-isolation-test  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/yourdb -e SPRING_DATASOURCE_USERNAME=youruser -e SPRING_DATASOURCE_PASSWORD=yourpassword  database-isolation-demo


我想借助CMS 15-445 的lecture https://www.youtube.com/watch?v=wO0PfAFiK3U 一口气搞清楚数据库的隔离级别和MVCC机制的实现。但是我不知道从哪里开始：先读课程？还是先读postgres的文档？还是先上手lab？如果你是一位专业人士，有更好的方法和建议吗？尽可能全面。

如果想通过一个示例数据库和一些sql来验证 隔离级别的工作方式，以便加深对概念的印象，强化记忆。我应该怎么做？

请问可以把上述的 shell 测试脚本，换spring boot 框架下的java 程序吗？结合Junit5框架来完成验证

请把上述的示例数据库、库表建表和初始化语句，spring boot程序源码和应用构建，整合为一个docker镜像，以便我能随时通过 docker run 命令运行这个程序，以及分发给我的其他同学



我想通过一个示例数据库和一些sql来验证 postgres 隔离级别的工作方式，以便加深对隔离级别的概念的印象，强化记忆。
我使用 spring boot 框架的java 程序，结合Junit5框架来完成验证。
示例数据库、库表建表和初始化语句，spring boot程序源码和应用构建，整合为一个docker镜像，以便我能随时通过 docker run 命令运行这个程序。
目前的代码库实现如上，但是不能正常工作，运行 IsolationLevelTest报错：
Failed to find Premain-Class manifest attribute in /Users/zhihuanzhou/.m2/repository/org/mockito/mockito-core/5.0.0/mockito-core-5.0.0.jar
Error occurred during initialization of VM
agent library failed Agent_OnLoad: instrument
请帮我修复它，使得能正常执行单元测试

本地调试

```base
docker run -d \
  --name my-postgres \
  -e POSTGRES_DB=yourdb \
  -e POSTGRES_USER=youruser \
  -e POSTGRES_PASSWORD=yourpassword \
  -p 5432:5432 \
  --platform linux/arm64 \
  postgres:15-alpine

```



我们知道 spring boot 提供的Isolation类，对应5中隔离级别：DEFAULT
READ_UNCOMMITTED
READ_COMMITTED
REPEATABLE_READ
SERIALIZABLE。这五种隔离级别有各自的特点，例如会出现 脏读 幻读 不可重复读等。
请你总结他们的特点，然后在 src/test/java/com/example/demo/isolev 目录下，
分别创建五种隔离级别对应的5个测试类，并添加测试方法，验证五种隔离级别不同的特点。
再创建第六个测试类验证 MVCC 的工作方式。 
要求：这些测试类都应该能执行通过，如果预期是抛异常，则 assert异常。 
这些测试可以通过 ./mvnw test 执行，互不干扰。 合理的创建sql 用户初始化。
初始化的sql文件请托管在 src/test/resources/db/testdata 目录下。