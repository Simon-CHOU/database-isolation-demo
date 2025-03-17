# 使用官方的Maven镜像作为构建基础
FROM maven:3.8.4-openjdk-17-slim AS build

# 构建应用程序并运行测试
RUN mvn clean package

WORKDIR /app

# 复制pom.xml和源代码
COPY pom.xml .
COPY src ./src

# 构建应用程序
RUN mvn clean package -DskipTests

# 使用官方的OpenJDK镜像作为运行基础
FROM openjdk:17-jdk-slim

WORKDIR /app

# 从构建阶段复制JAR文件
COPY --from=build /app/target/*.jar app.jar

# 暴露端口 (如果需要)
# EXPOSE 8080

# 运行应用程序 (这里不直接运行测试，而是运行主程序)
CMD ["java", "-jar", "app.jar"]