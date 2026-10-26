FROM openjdk:17-ea

COPY target/Docker_Aug2026-0.0.1-SNAPSHOT.jar app.jar

#java -jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]




#commands
# docker build -t aks2307/appimage:v0.0.1 .
# docker run -d --name "container1" -p 9000:8080 aks2307/appimage:v0.0.1