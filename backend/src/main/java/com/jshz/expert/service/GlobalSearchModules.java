package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import static com.jshz.expert.service.ModuleSearcher.QuerySpec;

/** Six independent registered searchers; SQL identifiers never come from request parameters. */
@Component
public class GlobalSearchModules {
    private final Map<String,ModuleSearcher> modules;
    public GlobalSearchModules() {
        Map<String,ModuleSearcher> registered=new LinkedHashMap<>();
        registered.put("knowledge",(user,now)->new QuerySpec("knowledge_document t","t.title","t.category",
                List.of("t.title","t.file_name","t.content"),"t.status=1",List.of(),"t.update_time"));
        registered.put("expert",(user,now)->new QuerySpec("expert_info t","t.name","t.unit",
                List.of("t.name","t.unit"),"1=1",List.of(),"t.update_time"));
        registered.put("project",(user,now)-> {
            String where="t.status<>'DELETED'";
            List<Object> args=new ArrayList<>();
            if(!user.hasPermission("project:view-all")) {
                where+=" AND (t.owner_id=? OR EXISTS(SELECT 1 FROM project_executor e WHERE e.project_id=t.id AND e.user_id=?))";
                args.add(user.getUserId());args.add(user.getUserId());
            }
            return new QuerySpec("project t LEFT JOIN project_config_snapshot snap ON snap.project_id=t.id","t.name","t.status",
                    List.of("t.name","CAST(t.id AS CHAR)"),where,args,"t.update_time");
        });
        registered.put("customer",(user,now)->new QuerySpec("customer t","t.name","t.work_unit",
                List.of("t.name"),"t.deleted=0",List.of(),"t.update_time"));
        registered.put("supplier",(user,now)->new QuerySpec("supplier t LEFT JOIN supplier_type st ON st.id=t.type_id LEFT JOIN supplier_city sc ON sc.id=t.city_id",
                "t.name","CONCAT_WS(' / ',st.name,sc.name)",List.of("t.name","st.name","sc.name"),"1=1",List.of(),"t.update_time"));
        registered.put("announcement",(user,now)->new QuerySpec("announcement t","t.title","CAST(t.create_time AS CHAR)",
                List.of("t.title","t.content"),"t.status='ONLINE' AND t.effective_time<=? AND (t.expire_time IS NULL OR t.expire_time>?)",List.of(now,now),"t.update_time"));
        modules=Collections.unmodifiableMap(registered);
    }
    public ModuleSearcher.QuerySpec query(String module, AuthUser user, LocalDateTime now) {
        ModuleSearcher searcher=modules.get(module);
        if(searcher==null) throw new BusinessException(400,"模块参数无效");
        return searcher.query(user,now);
    }
}
