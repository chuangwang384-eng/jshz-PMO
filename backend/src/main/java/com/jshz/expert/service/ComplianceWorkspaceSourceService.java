package com.jshz.expert.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.nio.file.Files;

@Service
@RequiredArgsConstructor
public class ComplianceWorkspaceSourceService {
 private final JdbcTemplate db;
 private final ObjectMapper json;
 private final ComplianceV4AccessService access;
 private final KnowledgeDocumentMapper documents;
 private final KnowledgeDocStorageService storage;
 private final KnowledgeDocTextExtractor extractor;
 private final org.springframework.transaction.PlatformTransactionManager manager;
 public String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new BusinessException("内容无法序列化");}}
 public Map<String,Object> decode(String s){try{return json.readValue(s,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new BusinessException("来源引用格式无效");}}
 public String hash(Object o){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(encode(canonical(o)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Object canonical(Object o){if(o instanceof Map<?,?> m){Map<String,Object> r=new TreeMap<>();m.forEach((k,v)->r.put(k.toString(),canonical(v)));return r;}if(o instanceof List<?> l)return l.stream().map(this::canonical).toList();return o;}
 public Map<String,Object> extract(long id,AuthUser user){
  access.requireDraftAuthor(id,user);var doc=documents.selectById(id);
  KnowledgeDocTextExtractor.StructuredExtraction extracted;
  try{extracted=extractor.extractStructured(Files.readAllBytes(storage.resolveDocument(doc.getFilePath())),doc.getFileName());}catch(Exception e){throw new BusinessException("读取规范文件失败，请核对原件");}
  String extractionId=UUID.randomUUID().toString();List<Map<String,Object>> candidates=new ArrayList<>();List<String> missing=new ArrayList<>();Set<String> seen=new HashSet<>();boolean truncated=false;
  for(var block:extracted.blocks()){
   if("PAGE".equals(block.kind())) missing.add(block.anchor());
   for(String line:block.text().split("\\R")){
    String text=line.trim();if(text.length()<8 || text.matches("^[\\d\\s.、，；;：:()（）-]+$"))continue;
    if(candidates.size()>=200){truncated=true;break;}
    if(text.length()>180){text=text.substring(0,180);truncated=true;}
    String candidateId=hash(Map.of("version",doc.getVersion(),"anchor",block.anchor(),"text",text));
    if(!seen.add(candidateId))continue;
    Map<String,Object> source=Map.of("extractionId",extractionId,"candidateId",candidateId,"sourceVersion",doc.getVersion(),"anchor",block.anchor(),"excerpt",text,"needsReview",true);
    candidates.add(Map.of("candidateId",candidateId,"title",text.length()>120?text.substring(0,120):text,"acceptance",text,"sourceAnchor",encode(source)));
   }
  }
  Map<String,Object> result=new LinkedHashMap<>();result.put("extractionId",extractionId);result.put("sourceVersion",doc.getVersion());result.put("candidates",candidates);result.put("incomplete",extracted.incomplete() || truncated);result.put("missingAnchors",missing);result.put("warning",Objects.toString(extracted.warning(),"")+(truncated?"；候选超过长度或数量限制，已截断，请人工补齐":""));
  return new TransactionTemplate(manager).execute(status->{
   db.queryForList("SELECT id FROM knowledge_document WHERE id=? FOR UPDATE",id);access.requireDraftAuthor(id,user);
   if(!Objects.equals(doc.getVersion(),documents.selectById(id).getVersion()))throw new BusinessException(409,"来源已更新，请重新提取");
   db.update("INSERT INTO compliance_template_extraction(id,document_id,source_version,result) VALUES(?,?,?,?)",extractionId,id,doc.getVersion(),encode(result));return result;
  });
 }
 public Map<String,Object> extraction(long id,String extractionId,AuthUser user){
  access.requireDraftAuthor(id,user);var rows=db.queryForList("SELECT source_version,result,confirmed_by FROM compliance_template_extraction WHERE id=? AND document_id=?",extractionId,id);
  if(rows.isEmpty())throw new BusinessException(404,"提取结果不存在");
  if(((Number)rows.get(0).get("source_version")).intValue()!=documents.selectById(id).getVersion())throw new BusinessException(409,"来源文档已更新，请重新提取和核对");
  var result=decode(rows.get(0).get("result").toString());result.put("confirmed",rows.get(0).get("confirmed_by")!=null);return result;
 }
 @SuppressWarnings("unchecked") public Map<String,Object> candidate(long id,String extractionId,String candidateId,AuthUser user){
  var result=extraction(id,extractionId,user);for(Object c:(List<?>)result.get("candidates")){var row=(Map<String,Object>)c;if(candidateId.equals(row.get("candidateId")))return row;}throw new BusinessException("来源候选不匹配");
 }
 public void verify(long id,Map<String,Object> node,AuthUser user){
  String value=Objects.toString(node.get("sourceAnchor"),"");if(!value.trim().startsWith("{"))return;
  var source=decode(value);if(!source.containsKey("extractionId"))return;
  var expected=decode(candidate(id,Objects.toString(source.get("extractionId"),""),Objects.toString(source.get("candidateId"),""),user).get("sourceAnchor").toString());
  for(String field:List.of("extractionId","candidateId","sourceVersion","anchor","excerpt"))if(!Objects.toString(expected.get(field),"").equals(Objects.toString(source.get(field),"")))throw new BusinessException("原文引用被修改，请重新选择来源候选");
 }
 public String reviewHash(Map<String,Object> node){Map<String,Object> content=new TreeMap<>(node);content.remove("id");content.remove("sort");content.remove("parentId");return hash(content);}
}
