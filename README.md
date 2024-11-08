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

## Properties configuration  
After that, configure the "azurekeys-region-template.props", inside the src/main/resources folder with the respective keys/urls from the resources created

Also, configure both the hibernate.cfg.xml files in "src/main/resources" and "src/webapp/WEB-INF/classes" with the CosmosDB PostegreSQL connection url and username/password

After deploying the Azure functions, insert the function url's inside the azurekeys-region-template.props

In the pom.xml, fill the properties block with the functionAppName, functionRegion, functionStorageAccountName and functionResourceGroup

Finally, rename the azure-region-template.props to azurekeys-region.props (remove the '-template')

## Deploying the app
To deploy the Azure Functions, run:  (don't forget to set up the function url's)
```
mvn clean compile package azure-functions:deploy
```

To deploy the web app, run: 
```
mvn clean compile package azure-webapp:deploy
```

To run artillery tests, run:
```
artillery run artillery/test_tukano.yaml
```




