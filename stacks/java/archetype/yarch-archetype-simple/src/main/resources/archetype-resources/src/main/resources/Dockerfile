# 多阶段构建：JDK 25 构建（字节码 release 21），JRE 25 运行（紧凑对象头/AOT 红利，J3b）
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q dependency:go-offline || true
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
# A7 基线：OOM dump、Xms=Xmx（容器内用等比 RAM 百分比实现，效果同）
ENV JAVA_OPTS="-XX:+HeapDumpOnOutOfMemoryError -XX:InitialRAMPercentage=60.0 -XX:MaxRAMPercentage=60.0"
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
