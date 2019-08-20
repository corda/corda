resourceGroup=testResourceGroup-$USER
az group delete --name $resourceGroup
az ad sp delete --id http://MyServicePrincipal-$USER
rm out.pkcs12