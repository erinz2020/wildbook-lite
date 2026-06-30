package com.wildme.wildbook_lite.bulkimport;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractRowImporter implements RowImporter {

    @Override
    public void importRow(List<String> fieldNames, List<String> row) {
        if(fieldNames.size() != row.size()) {
            throw new IllegalArgumentException("column count mismatch");
        }

        Map<String, String> fields = new HashMap<>();
        for(int i = 0; i < fieldNames.size(); i++) {
            fields.put(fieldNames.get(i), row.get(i));
        }

        persist(fields);
    }

    protected abstract void persist(Map<String, String> fields);
}