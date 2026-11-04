import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.service.ProjectConfigurationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only release evidence; never prints business fields or credentials. */
public final class ProjectConfigReleaseVerifier {
    public static void main(String[] args) throws Exception {
        String database=required("PC_MIGRATION_DATABASE");
        JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource(required("PC_MIGRATION_URL"),required("PC_MIGRATION_USER"),required("PC_MIGRATION_PASSWORD")));
        if(!database.equals(db.queryForObject("SELECT DATABASE()",String.class)))throw new IllegalStateException("Database mismatch");
        ObjectMapper json=new ObjectMapper();Map<String,Object> result=new LinkedHashMap<>(),hashes=new TreeMap<>();
        for(String table:List.of("project","project_doc","project_phase_history","project_executor","project_expert","project_supplier","project_contract","project_phase","project_template","project_phase_template_rel")) {
            List<Map<String,Object>> records=new ArrayList<>();
            for(Map<String,Object> row:db.queryForList("SELECT * FROM "+table+" ORDER BY id")) {
                Map<String,Object> normalized=new TreeMap<>();row.forEach((k,v)->normalized.put(k,v==null?null:v.toString()));records.add(normalized);
            }
            hashes.put(table,Map.of("rows",records.size(),"sha256",hash(json.writeValueAsString(records))));
        }
        List<Map<String,Object>> names=db.queryForList("SELECT id,name FROM project_type ORDER BY id");
        result.put("business",hashes);result.put("typeNamesHash",hash(json.writeValueAsString(names)));
        result.put("database",database);result.put("projects",db.queryForObject("SELECT COUNT(*) FROM project",Long.class));
        if(!Arrays.asList(args).contains("--baseline")) {
            Set<Long> allowed=new HashSet<>();for(String arg:args)if(arg.startsWith("--excluded="))for(String id:arg.substring(11).split(","))allowed.add(Long.parseLong(id));
            ProjectConfigurationService config=new ProjectConfigurationService(db,json);List<Long> excluded=new ArrayList<>();int verified=0;
            for(Map<String,Object> project:db.queryForList("SELECT p.id,p.phase_id,p.status,p.type_id,t.id existing_type FROM project p LEFT JOIN project_type t ON p.type_id=t.id ORDER BY p.id")) {
                long id=((Number)project.get("id")).longValue();
                if(allowed.contains(id)) {
                    if(!"DELETED".equals(project.get("status"))||project.get("existing_type")!=null||project.get("type_id")==null)throw new IllegalStateException("Exclusion predicate changed");
                    if(db.queryForObject("SELECT COUNT(*) FROM project_config_snapshot WHERE project_id=?",Long.class,id)!=0)throw new IllegalStateException("Excluded record was modified");
                    excluded.add(id);continue;
                }
                config.phase(config.snapshot(id),((Number)project.get("phase_id")).longValue());verified++;
            }
            if(!new HashSet<>(excluded).equals(allowed))throw new IllegalStateException("Excluded project set differs");
            List<Integer> positions=db.queryForList("SELECT sort FROM project_type ORDER BY sort",Integer.class);
            for(int i=0;i<positions.size();i++)if(positions.get(i)==null||positions.get(i)!=i+1)throw new IllegalStateException("Type positions invalid");
            if(db.queryForObject("SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project_type' AND INDEX_NAME='uk_project_type_position' AND NON_UNIQUE=0",Long.class)!=1)throw new IllegalStateException("Unique index missing");
            result.put("verifiedSnapshots",verified);result.put("excludedProjectIds",excluded);result.put("typePositions",positions);result.put("revisions",db.queryForObject("SELECT COUNT(*) FROM project_type_config_revision",Long.class));
        }
        result.put("result","PASS");System.out.println(json.writeValueAsString(result));
    }
    private static String required(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+name);return value;}
    private static String hash(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
}
