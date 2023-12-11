package io.edurt.datacap.service.service.impl;

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import io.edurt.datacap.common.enums.ServiceState;
import io.edurt.datacap.common.response.CommonResponse;
import io.edurt.datacap.common.sql.SqlBuilder;
import io.edurt.datacap.common.sql.configure.SqlBody;
import io.edurt.datacap.common.sql.configure.SqlColumn;
import io.edurt.datacap.common.sql.configure.SqlOperator;
import io.edurt.datacap.common.sql.configure.SqlOrder;
import io.edurt.datacap.common.sql.configure.SqlType;
import io.edurt.datacap.common.utils.CSVUtils;
import io.edurt.datacap.common.utils.SpiUtils;
import io.edurt.datacap.fs.Fs;
import io.edurt.datacap.fs.FsRequest;
import io.edurt.datacap.fs.FsResponse;
import io.edurt.datacap.service.body.ExportBody;
import io.edurt.datacap.service.body.TableFilter;
import io.edurt.datacap.service.common.PluginUtils;
import io.edurt.datacap.service.entity.SourceEntity;
import io.edurt.datacap.service.entity.UserEntity;
import io.edurt.datacap.service.entity.metadata.ColumnEntity;
import io.edurt.datacap.service.entity.metadata.DatabaseEntity;
import io.edurt.datacap.service.entity.metadata.TableEntity;
import io.edurt.datacap.service.initializer.InitializerConfigure;
import io.edurt.datacap.service.repository.metadata.TableRepository;
import io.edurt.datacap.service.security.UserDetailsService;
import io.edurt.datacap.service.service.TableService;
import io.edurt.datacap.spi.FormatType;
import io.edurt.datacap.spi.Plugin;
import io.edurt.datacap.spi.model.Configure;
import io.edurt.datacap.spi.model.Pagination;
import io.edurt.datacap.spi.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TableServiceImpl
        implements TableService
{
    private final Injector injector;
    private final TableRepository repository;
    private final InitializerConfigure initializerConfigure;
    private final HttpServletRequest request;

    public TableServiceImpl(Injector injector, TableRepository repository, InitializerConfigure initializerConfigure, HttpServletRequest request)
    {
        this.injector = injector;
        this.repository = repository;
        this.initializerConfigure = initializerConfigure;
        this.request = request;
    }

    @Override
    public CommonResponse<List<TableEntity>> getAllByDatabase(Long id)
    {
        DatabaseEntity database = DatabaseEntity.builder()
                .id(id)
                .build();
        return CommonResponse.success(this.repository.findAllByDatabase(database));
    }

    @Override
    public CommonResponse<Object> fetchDataById(Long id, TableFilter configure)
    {
        TableEntity table = this.repository.findById(id)
                .orElse(null);
        if (table == null) {
            return CommonResponse.failure(String.format("Table [ %s ] not found", id));
        }

        SourceEntity source = table.getDatabase().getSource();
        Optional<Plugin> pluginOptional = PluginUtils.getPluginByNameAndType(this.injector, source.getType(), source.getProtocol());
        if (!pluginOptional.isPresent()) {
            return CommonResponse.failure(ServiceState.PLUGIN_NOT_FOUND);
        }
        Plugin plugin = pluginOptional.get();
        if (configure.getType().equals(SqlType.SELECT)) {
            return this.fetchSelect(plugin, table, source, configure);
        }
        else if (configure.getType().equals(SqlType.INSERT)) {
            return this.fetchInsert(plugin, table, source, configure);
        }
        else if (configure.getType().equals(SqlType.UPDATE)) {
            return this.fetchUpdate(plugin, table, source, configure);
        }
        else if (configure.getType().equals(SqlType.DELETE)) {
            return this.fetchDelete(plugin, table, source, configure);
        }
        else if (configure.getType().equals(SqlType.ALTER)) {
            return this.fetchAlter(plugin, table, source, configure);
        }
        else if (configure.getType().equals(SqlType.SHOW)) {
            return this.fetchShowCreateTable(plugin, table, source, configure);
        }
        else if (configure.getType().equals(SqlType.TRUNCATE)) {
            return this.fetchTruncateTable(plugin, table, source, configure);
        }
        else if (configure.getType().equals(SqlType.DROP)) {
            return this.fetchDropTable(plugin, table, source, configure);
        }
        return CommonResponse.failure(String.format("Not implemented yet [ %s ]", configure.getType()));
    }

    @Override
    public CommonResponse exportDataById(Long id, ExportBody configure)
    {
        TableEntity table = this.repository.findById(id)
                .orElse(null);
        if (table == null) {
            return CommonResponse.failure(String.format("Table [ %s ] not found", id));
        }

        SourceEntity source = table.getDatabase().getSource();
        Optional<Plugin> optionalPlugin = PluginUtils.getPluginByNameAndType(this.injector, source.getType(), source.getProtocol());
        if (!optionalPlugin.isPresent()) {
            return CommonResponse.failure(String.format("Plugin [ %s ] not found", source.getType()));
        }

        Optional<Fs> optionalFs = SpiUtils.findFs(injector, initializerConfigure.getFsConfigure().getType());
        if (!optionalFs.isPresent()) {
            return CommonResponse.failure(String.format("Not found Fs [ %s ]", initializerConfigure.getFsConfigure().getType()));
        }

        UserEntity user = UserDetailsService.getUser();
        String endpoint = String.join(File.separator, initializerConfigure.getDataHome(), user.getUsername(), "export");
        log.info("Export data user [ {} ] home [ {} ]", user.getUsername(), endpoint);
        String fileName = String.join(".", UUID.randomUUID().toString(), "csv");
        log.info("Export file name [ {} ]", fileName);
        Integer count = Integer.MAX_VALUE;
        if (configure.getCount() > 0) {
            count = configure.getCount().intValue();
        }
        Pagination pagination = Pagination.newInstance(count.intValue(), 1, count.intValue());
        TableFilter tableFilter = TableFilter.builder()
                .type(SqlType.SELECT)
                .pagination(pagination)
                .build();
        CommonResponse<Object> tempResponse = this.fetchSelect(optionalPlugin.get(), table, source, tableFilter);
        if (tempResponse.getStatus()) {
            Response pluginResponse = (Response) tempResponse.getData();
            try {
                File tempFile = CSVUtils.makeTempCSV(endpoint, fileName, pluginResponse.getHeaders(), pluginResponse.getColumns());
                log.info("Export temp file [ {} ]", tempFile.getAbsolutePath());
                FsRequest fsRequest = FsRequest.builder()
                        .access(initializerConfigure.getFsConfigure().getAccess())
                        .secret(initializerConfigure.getFsConfigure().getSecret())
                        .endpoint(endpoint)
                        .bucket(initializerConfigure.getFsConfigure().getBucket())
                        .stream(Files.newInputStream(tempFile.toPath()))
                        .fileName(fileName)
                        .build();
                FsResponse fsResponse = optionalFs.get().writer(fsRequest);
                if (initializerConfigure.getFsConfigure().getType().equals("Local")) {
                    String address = request.getRequestURL()
                            .toString()
                            .replace(request.getServletPath().trim(), "");
                    String remote = String.join("/", address, "api/v1/table/dataDownload", user.getUsername(), fileName);
                    return CommonResponse.success(remote);
                }
            }
            catch (Exception ex) {
                return CommonResponse.failure(ex.getMessage());
            }
        }
        return CommonResponse.failure(ServiceState.REQUEST_EXCEPTION);
    }

    /**
     * Fetches the selected data from the specified table in a database.
     *
     * @param plugin the plugin instance
     * @param table the table entity
     * @param source the source entity
     * @param configure the table filter configuration
     * @return the common response object containing the fetched data
     */
    private CommonResponse<Object> fetchSelect(Plugin plugin, TableEntity table, SourceEntity source, TableFilter configure)
    {
        try {
            List<SqlColumn> columns = Lists.newArrayList();
            int totalRows = Integer.parseInt(table.getRows());
            Configure countConfigure = source.toConfigure();
            countConfigure.setFormat(FormatType.NONE);
            plugin.connect(countConfigure);
            SqlBody countBody = SqlBody.builder()
                    .type(SqlType.SELECT)
                    .database(table.getDatabase().getName())
                    .table(table.getName())
                    .columns(Arrays.asList(SqlColumn.builder()
                            .column("COUNT(1)")
                            .build()))
                    .build();
            if (configure.getFilter() != null) {
                countBody.setCondition(configure.getFilter().getCondition());
                countBody.setWhere(configure.getFilter().getFilters());
            }
            SqlBuilder countBuilder = new SqlBuilder(countBody);
            String countSql = countBuilder.getSql();
            Response countResponse = plugin.execute(countSql);
            plugin.destroy();
            if (countResponse.getIsSuccessful() && !countResponse.getColumns().isEmpty()) {
                List<Object> applyResponse = (ArrayList) countResponse.getColumns().get(0);
                int applyTotal = Integer.parseInt(String.valueOf(applyResponse.get(0)));
                if (totalRows != applyTotal) {
                    totalRows = applyTotal;
                    table.setRows(String.valueOf(totalRows));
                    this.repository.save(table);
                }
            }

            // If the columns of the query are not passed, they are obtained through metadata
            if (configure.getColumns() == null || configure.getColumns().size() == 0) {
                table.getColumns()
                        .stream()
                        .sorted(Comparator.comparing(item -> Integer.parseInt(item.getPosition())))
                        .forEach(column -> columns.add(SqlColumn.builder()
                                .column(String.format("`%s`", column.getName()))
                                .build()));
            }
            else {
                configure.getColumns()
                        .forEach(column -> columns.add(SqlColumn.builder()
                                .column(String.format("`%s`", column.getColumn()))
                                .build()));
            }
            int offset = configure.getPagination().getPageSize() * (configure.getPagination().getCurrentPage() - 1);
            SqlBody body = SqlBody.builder()
                    .type(SqlType.SELECT)
                    .database(table.getDatabase().getName())
                    .table(table.getName())
                    .columns(columns)
                    .limit(configure.getPagination().getPageSize())
                    .offset(offset)
                    .build();

            if (configure.getOrders() != null) {
                List<SqlColumn> orderColumns = Lists.newArrayList();
                configure.getOrders()
                        .stream()
                        .filter(item -> StringUtils.isNotEmpty(item.getOrder()))
                        .forEach(item -> orderColumns.add(SqlColumn.builder()
                                .column(item.getColumn())
                                .order(SqlOrder.valueOf(item.getOrder().toUpperCase()))
                                .build()));
                body.setOrders(orderColumns);
            }

            if (configure.getFilter() != null) {
                body.setCondition(configure.getFilter().getCondition());
                body.setWhere(configure.getFilter().getFilters());
            }

            SqlBuilder builder = new SqlBuilder(body);
            String sql = builder.getSql();
            plugin.connect(source.toConfigure());
            Response response = plugin.execute(sql);
            response.setContent(sql);
            plugin.destroy();
            Pagination pagination = Pagination.newInstance(configure.getPagination().getPageSize(), configure.getPagination().getCurrentPage(), totalRows);
            response.setPagination(pagination);
            return CommonResponse.success(response);
        }
        catch (Exception ex) {
            return CommonResponse.failure(ExceptionUtils.getMessage(ex));
        }
    }

    /**
     * Fetches and inserts data into the database table.
     *
     * @param plugin the plugin object
     * @param table the table entity object
     * @param source the source entity object
     * @param configure the table filter object
     * @return the common response object containing the result of the operation
     */
    private CommonResponse<Object> fetchInsert(Plugin plugin, TableEntity table, SourceEntity source, TableFilter configure)
    {
        try {
            Configure updateConfigure = source.toConfigure();
            updateConfigure.setFormat(FormatType.NONE);
            plugin.connect(updateConfigure);
            List<String> allSql = Lists.newArrayList();
            // Gets the auto-increment column for the current row
            List<String> autoIncrementColumns = table.getColumns()
                    .stream()
                    .filter(v -> v.getExtra().toLowerCase().contains("auto_increment"))
                    .map(v -> v.getName())
                    .collect(Collectors.toList());
            configure.getNewColumns().forEach(v -> {
                List<SqlColumn> columns = Lists.newArrayList();
                v.entrySet().forEach(entry -> {
                    SqlColumn column = SqlColumn.builder()
                            .column(String.format("`%s`", entry.getKey()))
                            .build();
                    if (entry.getValue() == null) {
                        column.setValue(null);
                    }
                    else {
                        if (autoIncrementColumns.contains(entry.getKey())) {
                            column.setValue(null);
                        }
                        else {
                            column.setValue(String.format("'%s'", StringEscapeUtils.escapeSql(entry.getValue())));
                        }
                    }
                    columns.add(column);
                });
                SqlBody body = SqlBody.builder()
                        .type(SqlType.INSERT)
                        .database(table.getDatabase().getName())
                        .table(table.getName())
                        .columns(columns)
                        .build();
                allSql.add(new SqlBuilder(body).getSql());
            });
            String sql = String.join("\n\n", allSql);
            return CommonResponse.success(getResponse(configure, plugin, sql));
        }
        catch (Exception ex) {
            return CommonResponse.failure(ExceptionUtils.getMessage(ex));
        }
    }

    /**
     * Fetches and updates data from a table using the specified plugin, table, source, and configure.
     *
     * @param plugin the plugin to be used for fetching and updating data
     * @param table the table from which the data will be fetched and updated
     * @param source the source entity containing the data source configuration
     * @param configure the table filter configuration for fetching and updating the data
     * @return the response containing the fetched and updated data
     */
    private CommonResponse<Object> fetchUpdate(Plugin plugin, TableEntity table, SourceEntity source, TableFilter configure)
    {
        try {
            Configure updateConfigure = source.toConfigure();
            updateConfigure.setFormat(FormatType.NONE);
            plugin.connect(updateConfigure);
            List<String> allSql = Lists.newArrayList();
            configure.getColumns().forEach(v -> {
                SqlBody body = SqlBody.builder()
                        .type(SqlType.UPDATE)
                        .database(table.getDatabase().getName())
                        .table(table.getName())
                        .columns(Arrays.asList(v))
                        .where(applyColumns(table, configure, v)
                                .stream()
                                .distinct()
                                .collect(Collectors.toList()))
                        .build();
                allSql.add(new SqlBuilder(body).getSql());
            });
            String sql = String.join("\n\n", allSql);
            return CommonResponse.success(getResponse(configure, plugin, sql));
        }
        catch (Exception ex) {
            return CommonResponse.failure(ExceptionUtils.getMessage(ex));
        }
    }

    /**
     * Fetches and deletes data from the database.
     *
     * @param plugin the plugin to use for connecting to the database
     * @param table the table from which to delete the data
     * @param source the source entity to use for configuring the connection
     * @param configure the table filter to use for filtering the data to be deleted
     * @return the response containing the result of the delete operation
     */
    private CommonResponse<Object> fetchDelete(Plugin plugin, TableEntity table, SourceEntity source, TableFilter configure)
    {
        try {
            Configure updateConfigure = source.toConfigure();
            updateConfigure.setFormat(FormatType.NONE);
            plugin.connect(updateConfigure);
            List<String> allSql = Lists.newArrayList();
            configure.getColumns().forEach(v -> {
                SqlBody body = SqlBody.builder()
                        .type(SqlType.DELETE)
                        .database(table.getDatabase().getName())
                        .table(table.getName())
                        .where(applyColumns(table, configure, v))
                        .build();
                allSql.add(new SqlBuilder(body).getSql());
            });
            String sql = String.join("\n\n", allSql);
            return CommonResponse.success(getResponse(configure, plugin, sql));
        }
        catch (Exception ex) {
            return CommonResponse.failure(ExceptionUtils.getMessage(ex));
        }
    }

    /**
     * Fetches an alter operation for a given plugin, table, source, and table filter.
     *
     * @param plugin the plugin to use for the alter operation
     * @param table the table entity to perform the alter operation on
     * @param source the source entity to configure the alter operation
     * @param configure the table filter to apply to the alter operation
     * @return a CommonResponse object containing the result of the alter operation
     */
    private CommonResponse<Object> fetchAlter(Plugin plugin, TableEntity table, SourceEntity source, TableFilter configure)
    {
        try {
            Configure alterConfigure = source.toConfigure();
            alterConfigure.setFormat(FormatType.NONE);
            plugin.connect(alterConfigure);
            SqlBody body = SqlBody.builder()
                    .type(SqlType.ALTER)
                    .database(table.getDatabase().getName())
                    .table(table.getName())
                    .value(configure.getValue())
                    .build();
            return CommonResponse.success(getResponse(configure, plugin, new SqlBuilder(body).getSql()));
        }
        catch (Exception ex) {
            return CommonResponse.failure(ExceptionUtils.getMessage(ex));
        }
    }

    /**
     * Fetches the "SHOW CREATE TABLE" query for a given table.
     *
     * @param plugin the plugin to use for connecting to the database
     * @param table the table entity for which to fetch the query
     * @param source the source entity containing the database configuration
     * @param configure the table filter configuration
     * @return the common response object containing the result of the query
     */
    private CommonResponse<Object> fetchShowCreateTable(Plugin plugin, TableEntity table, SourceEntity source, TableFilter configure)
    {
        try {
            Configure alterConfigure = source.toConfigure();
            alterConfigure.setFormat(FormatType.NONE);
            plugin.connect(alterConfigure);
            SqlBody body = SqlBody.builder()
                    .type(SqlType.SHOW)
                    .database(table.getDatabase().getName())
                    .table(table.getName())
                    .build();
            return CommonResponse.success(getResponse(configure, plugin, new SqlBuilder(body).getSql()));
        }
        catch (Exception ex) {
            return CommonResponse.failure(ExceptionUtils.getMessage(ex));
        }
    }

    /**
     * Fetches and truncates a table.
     *
     * @param plugin the plugin associated with the table
     * @param table the table entity to fetch and truncate
     * @param source the source entity associated with the table
     * @param configure the table filter configuration
     * @return the common response object containing the fetch and truncate result
     */
    private CommonResponse<Object> fetchTruncateTable(Plugin plugin, TableEntity table, SourceEntity source, TableFilter configure)
    {
        try {
            Configure alterConfigure = source.toConfigure();
            alterConfigure.setFormat(FormatType.NONE);
            plugin.connect(alterConfigure);
            SqlBody body = SqlBody.builder()
                    .type(SqlType.TRUNCATE)
                    .database(table.getDatabase().getName())
                    .table(table.getName())
                    .build();
            return CommonResponse.success(getResponse(configure, plugin, new SqlBuilder(body).getSql()));
        }
        catch (Exception ex) {
            return CommonResponse.failure(ExceptionUtils.getMessage(ex));
        }
    }

    /**
     * Fetches the drop table operation for the given table and source.
     *
     * @param plugin the plugin instance
     * @param table the table entity
     * @param source the source entity
     * @param configure the table filter configuration
     * @return the common response object containing the fetched result
     */
    private CommonResponse<Object> fetchDropTable(Plugin plugin, TableEntity table, SourceEntity source, TableFilter configure)
    {
        try {
            Configure alterConfigure = source.toConfigure();
            alterConfigure.setFormat(FormatType.NONE);
            plugin.connect(alterConfigure);
            SqlBody body = SqlBody.builder()
                    .type(SqlType.DROP)
                    .database(table.getDatabase().getName())
                    .table(table.getName())
                    .value(configure.getValue())
                    .build();
            Response response = getResponse(configure, plugin, new SqlBuilder(body).getSql());
            if (!configure.isPreview() && response.getIsSuccessful()) {
                repository.delete(table);
            }
            return CommonResponse.success(response);
        }
        catch (Exception ex) {
            return CommonResponse.failure(ExceptionUtils.getMessage(ex));
        }
    }

    /**
     * Applies the columns of a table to a given configuration.
     *
     * @param table the table entity containing the columns
     * @param configure the table filter configuration to apply the columns to
     * @param column the SQL column to apply
     * @return a list of SQL columns that match the applied configuration
     */
    private List<SqlColumn> applyColumns(TableEntity table, TableFilter configure, SqlColumn column)
    {
        List<SqlColumn> wheres = Lists.newArrayList();
        List<ColumnEntity> originColumns = table.getColumns()
                .stream()
                .filter(item -> item.getIsKey().equals("PRI"))
                .collect(Collectors.toList());
        if (originColumns.size() > 0) {
            // If the table contains a primary key, update the data using the primary key as a condition
            originColumns.forEach(item -> wheres.add(SqlColumn.builder()
                    .column(item.getName())
                    .operator(SqlOperator.EQ)
                    .value(String.valueOf(column.getOriginal().get(item.getName())))
                    .build()));
        }
        else {
            // If the current data does not have a primary key, it is updated based on all data columns
            table.getColumns()
                    .forEach(item -> wheres.add(SqlColumn.builder()
                            .column(item.getName())
                            .operator(SqlOperator.EQ)
                            .value(String.valueOf(column.getOriginal().get(item.getName())))
                            .build()));
        }
        return wheres;
    }

    /**
     * Retrieves a response based on the provided table filter, plugin, and SQL query.
     *
     * @param configure the table filter to configure the response
     * @param plugin the plugin to execute the SQL query
     * @param sql the SQL query to execute
     * @return the response containing the result of the SQL query
     */
    private Response getResponse(TableFilter configure, Plugin plugin, String sql)
    {
        Response response;
        if (configure.isPreview()) {
            response = Response.builder()
                    .isSuccessful(true)
                    .isConnected(true)
                    .headers(Lists.newArrayList())
                    .columns(Lists.newArrayList())
                    .types(Lists.newArrayList())
                    .content(sql)
                    .build();
        }
        else {
            response = plugin.execute(sql);
            plugin.destroy();
            response.setContent(sql);
        }
        return response;
    }
}
