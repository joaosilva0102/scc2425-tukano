# TUKANO SCC 24/25
João Silva | 70373 | jpms.silva@campus.fct.un.pt  
João Bernardo | 62612 | jmp.bernardo@campus.fct.unl.pt

## Before running the app
Before running the app, ensure you have the following Azure resources deployed (in the same resource group): 
- CosmosDB NoSQL
- CosmosDB PostegreSQL
- BlobStorage
- Redis Cache
- Functions App
- Web App
  
After that, configure the "azure-region-template.props", inside the src/main/resources folder with the respective keys/urls from the resources created

Also, configure the hibernate.cfg.xml file with the CosmosDB PostegreSQL connection url and username/password

After deploying the Azure functions, insert the function url's inside the azure-region-template.props

In the pom.xml, fill the properties block with the functionAppName, functionRegion, functionStorageAccountName and functionResourceGroup

Finally, rename the azure-region-template.props to azure-region.props (remove the '-template')

## Deploying the app
To deploy the web app, run: 
```
mvn clean compile package azure-webapp:deploy
```

To deploy the Azure Functions, run:  (don't forget to set up the function url's)
```
mvn clean compile package azure-functions:deploy
```


