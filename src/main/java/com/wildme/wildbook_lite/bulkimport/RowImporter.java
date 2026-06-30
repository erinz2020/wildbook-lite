package com.wildme.wildbook_lite.bulkimport;
import java.util.List;

public interface RowImporter {
    void importRow(
        List<String> fieldNames, List<String> row
    );

    String supportedType();
}