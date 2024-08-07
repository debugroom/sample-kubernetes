package org.debugroom.sample.kubernetes.servicemesh.domain;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.Export;
import com.amazonaws.services.cloudformation.model.ListExportsRequest;
import com.amazonaws.services.cloudformation.model.ListExportsResult;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Profile("aws")
@Component
public class CloudFormationStackResolver implements InitializingBean {

    @Autowired
    AmazonCloudFormation amazonCloudFormation;

    private List<Export> exportList = new ArrayList<>();

    public String getExportValue(String exportName){
        Optional<Export> targetExport = exportList.stream()
                .filter(export -> export.getName().equals(exportName))
                .findFirst();
        return targetExport.isPresent() ? targetExport.get().getValue() : null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ListExportsResult listExportsResult = amazonCloudFormation
                .listExports(new ListExportsRequest());
        List<Export> exportList = listExportsResult.getExports();
        while (100 == exportList.size()){
            this.exportList.addAll(exportList);
            listExportsResult = amazonCloudFormation.listExports(
                    new ListExportsRequest().withNextToken(listExportsResult.getNextToken()));
            exportList = listExportsResult.getExports();
        }
        this.exportList.addAll(exportList);
    }
}
