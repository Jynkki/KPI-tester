# load

Usage:

     Create a configuration.json file from the template
     mvn clean install
     java -jar target/kpi-demo-1.0.0-SNAPSHOT-jar-with-dependencies.jar -l

or put it into docker swarm with:

     mvn clean install
     docker-compose -f docker/docker-compose.yml build
     KPI_SECRET='configuration.json' docker stack deploy --compose-file docker/docker-compose.yml <name>
    

Delete it from docker swarm with:

     docker stack rm <name>
 
