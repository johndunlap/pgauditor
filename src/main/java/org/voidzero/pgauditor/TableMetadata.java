package org.voidzero.pgauditor;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TableMetadata {
    private String catalog;

    private String schema;

    private String tableName;

    private String tableType;

    private String remarks;

    private String typeCatalog;

    private String typeSchema;

    private String typeName;

    private String selfReferencingColName;

    private String refGeneration;

    /**
     * This list will contain one metadata object for each column in the result set.
     */
    private List<ColumnMetadata> columns = new ArrayList<>();

    public TableMetadata(final Connection connection, final Configuration config) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet tableResultSet = metaData.getTables(null, config.getSchema(), config.getTableOnly(), null);

        if (!tableResultSet.next()) {
            throw new SQLException("Table " + config.getTableWithSchema() + " does not exist");
        }

        this.catalog = tableResultSet.getString("TABLE_CAT");
        this.schema = tableResultSet.getString("TABLE_SCHEM");
        this.tableName = tableResultSet.getString("TABLE_NAME");
        this.tableType = tableResultSet.getString("TABLE_TYPE");
        this.remarks = tableResultSet.getString("REMARKS");
        this.typeCatalog = tableResultSet.getString("TYPE_CAT");
        this.typeSchema = tableResultSet.getString("TYPE_SCHEM");
        this.typeName = tableResultSet.getString("TYPE_NAME");
        this.selfReferencingColName = tableResultSet.getString("SELF_REFERENCING_COL_NAME");
        this.refGeneration = tableResultSet.getString("REF_GENERATION");

        ResultSet columnResultSet = metaData.getColumns(
                null,
                config.getSchema(),
                config.getTableOnly(),
                null
        );

        while (columnResultSet.next()) {
            columns.add(new ColumnMetadata(columnResultSet));
        }
    }

    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnMetadata> columns) {
        this.columns = columns;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getTypeCatalog() {
        return typeCatalog;
    }

    public void setTypeCatalog(String typeCatalog) {
        this.typeCatalog = typeCatalog;
    }

    public String getTypeSchema() {
        return typeSchema;
    }

    public void setTypeSchema(String typeSchema) {
        this.typeSchema = typeSchema;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getSelfReferencingColName() {
        return selfReferencingColName;
    }

    public void setSelfReferencingColName(String selfReferencingColName) {
        this.selfReferencingColName = selfReferencingColName;
    }

    public String getRefGeneration() {
        return refGeneration;
    }

    public void setRefGeneration(String refGeneration) {
        this.refGeneration = refGeneration;
    }

    @Override
    public String toString() {
        return "TableMetadata{" +
                "catalog='" + catalog + '\'' +
                ", schema='" + schema + '\'' +
                ", tableName='" + tableName + '\'' +
                ", tableType='" + tableType + '\'' +
                ", remarks='" + remarks + '\'' +
                ", typeCatalog='" + typeCatalog + '\'' +
                ", typeSchema='" + typeSchema + '\'' +
                ", typeName='" + typeName + '\'' +
                ", selfReferencingColName='" + selfReferencingColName + '\'' +
                ", refGeneration='" + refGeneration + '\'' +
                '}';
    }
}
