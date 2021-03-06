package fi.vm.sade.javautils.healthcheck;

import fi.vm.sade.javautils.healthcheck.HealthChecker;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DatabaseHealthChecker implements HealthChecker {

    private DataSource dataSource;

    public DatabaseHealthChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Object checkHealth() throws Throwable {
        if (dataSource != null) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            DatabaseMetaData dbMetaData = dataSource.getConnection().getMetaData();
            result.put("url", dbMetaData.getURL());
            ResultSet rs = dbMetaData.getTables(null, null, "DATA_STATUS", null);
            boolean dataStatusTableExists = rs.next();
            if (dataStatusTableExists) {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM data_status ORDER BY muutoshetki");
                result.put("data_status", list);
            }

            //
            // Get count information from database tables
            //
            Map<String,Object> counts = new HashMap<String, Object>();
            rs = dbMetaData.getTables(null, null, "%" ,new String[] {"TABLE"});
            while(rs.next()) {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                String tableName = rs.getString("TABLE_NAME");
                counts.put(tableName, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class));
            }
            result.put("counts", counts);

            return result;
        } else {
            return "N/A";
        }
    }
}
