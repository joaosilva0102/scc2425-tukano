FROM tomcat:10.0-jdk17-openjdk
WORKDIR /usr/local/tomcat
RUN mkdir -p /mnt/vol
ADD /target/blobs-1.war webapps
EXPOSE 8080