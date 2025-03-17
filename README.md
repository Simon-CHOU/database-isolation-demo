

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