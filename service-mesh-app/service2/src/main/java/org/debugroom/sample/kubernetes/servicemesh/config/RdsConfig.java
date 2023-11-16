package org.debugroom.sample.kubernetes.servicemesh.config;

import javax.sql.DataSource;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.debugroom.sample.kubernetes.servicemesh.domain.CloudFormationStackResolver;

@Configuration
public class RdsConfig {

    private static final String RDS_DB_NAME  = "debugroom-sample-kubernetes-vpc-RDS-DBName";
    private static final String RDS_ENDPOINT = "debugroom-sample-kubernetes-vpc-RDS-Endpoint";
    private static final String RDS_USERNAME = "debugroom-sample-kubernetes-vpc-RDS-UserName";
    private static final String RDS_PASSWORD = "debugroom-sample-kubernetes-vpc-RDS-UserPassword";

    @Bean
    AmazonCloudFormation amazonCloudFormation(){
        return AmazonCloudFormationClientBuilder.standard().build();
    }

    @Autowired
    CloudFormationStackResolver cloudFormationStackResolver;

    @Bean
    public DataSource dataSource(){
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName("org.postgresql.Driver");
        dataSourceBuilder.url("jdbc:postgresql://"
                + cloudFormationStackResolver.getExportValue(RDS_ENDPOINT)
                + ":5432/"
                + cloudFormationStackResolver.getExportValue(RDS_DB_NAME));
        dataSourceBuilder.username(
                cloudFormationStackResolver.getExportValue(RDS_USERNAME));
        dataSourceBuilder.password(
                cloudFormationStackResolver.getExportValue(RDS_PASSWORD));
        return dataSourceBuilder.build();
    }


}
