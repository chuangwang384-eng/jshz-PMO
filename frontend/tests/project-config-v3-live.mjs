import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || "playwright"
);
if (
  process.env.PC_ACCEPTANCE_DB !== "pc_acceptance" ||
  !process.env.PC_TEST_PASSWORD
)
  throw new Error("Explicit isolated fixture opt-in required");
const base = "http://localhost:5173",
  artifact = new URL(
    "../../.build-project-config-acceptance/evidence/",
    import.meta.url,
  );
await mkdir(artifact, { recursive: true });
const browser = await chromium.launch({ channel: "msedge", headless: true }),
  context = await browser.newContext({
    viewport: { width: 1440, height: 1100 },
  }),
  page = await context.newPage();
page.setDefaultTimeout(15000);
const errors = [],
  mutations = [];
page.on("pageerror", (e) => errors.push(e.message));
page.on("request", (r) => {
  if (
    r.method() !== "GET" &&
    new URL(r.url()).pathname.startsWith("/api/project/config")
  )
    mutations.push({ method: r.method(), path: new URL(r.url()).pathname });
});
async function api(client, path, method = "GET", data) {
  const response = await client.fetch(base + "/api" + path, { method, data });
  const text = await response.text();
  return {
    status: response.status(),
    ...(text ? JSON.parse(text) : { code: response.status() }),
  };
}
async function ok(path, method = "GET", data) {
  const r = await api(context.request, path, method, data);
  assert.equal(r.code, 0, `${method} ${path}: ${r.message}`);
  return r.data;
}
try {
  const anonymous = await api(
    context.request,
    "/project/config/definition-context",
  );
  assert.equal(anonymous.status, 401);
  await page.goto(base + "/login?redirect=/admin/project/config");
  await page.locator('input[autocomplete="username"]').fill("配置验收管理员");
  await page
    .getByPlaceholder("密码", { exact: true })
    .fill(process.env.PC_TEST_PASSWORD);
  await page.getByRole("button", { name: /登\s*录/ }).click();
  await page.getByText("项目类型流程总览", { exact: true }).waitFor();
  console.log("PASS login and authorized configuration page");
  const beforeTypes = await ok("/project/config/types"),
    beforePhases = await ok("/project/config/phases");
  const baseline = beforeTypes.find((t) => t.name === "浏览器基线类型");
  assert.ok(baseline);
  const baselineFlow = (await ok(`/project/config/types/${baseline.id}/phases`))
    .length;
  const typeName = "真实页面验收类型-" + Date.now(),
    firstStage = "现场审核-" + Date.now(),
    lastStage = "验收完成-" + Date.now();
  await page
    .getByRole("button", { name: "新增类型", exact: true })
    .first()
    .click();
  let dialog = page.getByRole("dialog", { name: "新增项目类型", exact: true });
  await dialog.getByPlaceholder("例如：展会项目").fill(typeName);
  await dialog.getByRole("button", { name: "下一步", exact: true }).click();
  console.log("Step 2 ready");
  await dialog.locator(".el-select:visible").first().locator("input").click();
  await page.getByRole("option", { name: "立项", exact: true }).click();
  await dialog
    .getByRole("button", { name: "添加已有阶段", exact: true })
    .click();
  for (const name of [firstStage, lastStage]) {
    await dialog.getByPlaceholder("没有合适的？输入新阶段名称").fill(name);
    await dialog
      .getByRole("button", { name: "新建并加入", exact: true })
      .click();
  }
  assert.equal(
    (await ok("/project/config/phases")).length,
    beforePhases.length,
    "New stages must remain draft-only before save",
  );
  await dialog.getByText('流程预览',{exact:true}).click();
  await dialog.getByRole('img',{name:'当前草稿阶段顺序预览'}).waitFor();
  await dialog.getByText('列表编辑',{exact:true}).click();
  await dialog.getByRole("button", { name: "下一步", exact: true }).click();
  await dialog.getByRole("button", { name: /确认本阶段/ }).click();
  await dialog.getByText("为此类型单独配置", { exact: true }).click();
  await dialog
    .locator("section .el-select:visible")
    .first()
    .locator("input")
    .click();
  await page
    .getByRole("option", { name: "资料附件 · 附件", exact: true })
    .click();
  await dialog.getByRole("button", { name: "加入清单", exact: true }).click();
  await dialog.getByRole("button", { name: /确认本阶段/ }).click();
  await dialog.getByText("无需提交材料", { exact: true }).first().click();
  await dialog.getByRole("button", { name: /确认本阶段/ }).click();
  await dialog.getByRole("button", { name: "预览与确认", exact: true }).click();
  await dialog
    .getByRole("heading", { name: "员工视角预览 · " + typeName })
    .waitFor();
  await page.screenshot({
    path: new URL("wizard-preview.png", artifact).pathname.replace(
      /^\/([A-Za-z]:)/,
      "$1",
    ),
    fullPage: true,
  });
  await dialog
    .getByText("已核对流程、材料要求和展示位置；已有项目保持原配置", {
      exact: true,
    })
    .click();
  const savedResponse = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === "/api/project/config/definitions" &&
      r.request().method() === "POST",
  );
  await dialog
    .getByRole("button", { name: "保存全部配置", exact: true })
    .click();
  assert.equal((await (await savedResponse).json()).code, 0);
  await dialog.waitFor({ state: "hidden" });
  const types = await ok("/project/config/types"),
    created = types.find((t) => t.name === typeName);
  assert.ok(created);
  assert.deepEqual(
    types.map((t) => t.sort),
    types.map((_, i) => i + 1),
  );
  const definition = await ok(`/project/config/types/${created.id}/definition`);
  assert.deepEqual(
    definition.phases.map((p) => p.mode),
    ["INHERIT", "CUSTOM", "NONE"],
  );
  assert.equal(definition.phases[1].requirements[0].required, 0);
  assert.equal(
    (await ok(`/project/config/types/${baseline.id}/phases`)).length,
    baselineFlow,
  );
  assert.equal(
    mutations.filter((m) => m.path === "/api/project/config/definitions")
      .length,
    1,
  );
  assert.equal(
    mutations.filter((m) => m.path === "/api/project/config/phases").length,
    0,
  );
  // Reopen and verify the atomic configuration was persisted as three distinct modes.
  await page
    .getByRole("row")
    .filter({ hasText: typeName })
    .getByRole("button", { name: "编辑", exact: true })
    .click();
  dialog = page.getByRole("dialog", { name: "编辑项目类型", exact: true });
  await dialog.getByPlaceholder("例如：展会项目").waitFor();
  await dialog.getByRole("button", { name: "下一步", exact: true }).click();
  await dialog.getByRole("button", { name: "下一步", exact: true }).click();
  await dialog.locator("nav").getByRole("button").nth(1).click();
  await dialog.getByText("资料附件", { exact: true }).waitFor();
  await dialog.getByRole("button", { name: "取消", exact: true }).click();
  await page.getByRole("button", { name: "确定", exact: true }).click();
  await dialog.waitFor({ state: "hidden" });
  // Rank operations are performed through the real order API from the UI.
  await page.getByRole("button", { name: "调整类型顺序", exact: true }).click();
  dialog = page.getByRole("dialog", { name: "调整类型展示顺序", exact: true });
  for (let i = types.length - 1; i > 0; i--)
    await dialog
      .locator(".order-row")
      .filter({ hasText: typeName })
      .getByRole("button", { name: "上移", exact: true })
      .click();
  await dialog.getByRole("button", { name: "保存顺序", exact: true }).click();
  await dialog.waitFor({ state: "hidden" });
  assert.equal((await ok("/project/config/types"))[0].id, created.id);
  // Cancellation must leave the dictionary untouched.
  const phaseCount = (await ok("/project/config/phases")).length;
  await page
    .getByRole("button", { name: "新增类型", exact: true })
    .first()
    .click();
  dialog = page.getByRole("dialog", { name: "新增项目类型", exact: true });
  await dialog.getByPlaceholder("例如：展会项目").fill("取消的草稿");
  await dialog.getByRole("button", { name: "下一步", exact: true }).click();
  await dialog
    .getByPlaceholder("没有合适的？输入新阶段名称")
    .fill("不得落库的阶段");
  await dialog.getByRole("button", { name: "新建并加入", exact: true }).click();
  await dialog.getByRole("button", { name: "取消", exact: true }).click();
  await page.getByRole("button", { name: "确定", exact: true }).click();
  await dialog.waitFor({ state: "hidden" });
  assert.equal((await ok("/project/config/phases")).length, phaseCount);
  const project = await ok("/project", "POST", {
    name: "浏览器隔离项目",
    category: "OFFLINE",
    typeId: created.id,
    ownerId: 1,
  });
  const snap = await ok(`/project/${project.id}/configuration`);
  assert.equal(snap.name, typeName);
  await page.goto(`${base}/project/${project.id}`);
  await page.getByText('阶段资料 · 立项',{exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'推进下一阶段',exact:true}).first().isEnabled(),false);
  await page.locator('.pd-phase input[type="file"]').first().setInputFiles({name:'隔离验收资料.txt',mimeType:'text/plain',buffer:Buffer.from('Synthetic acceptance material only')});
  await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='推进下一阶段'&&!b.disabled));
  for(const name of [firstStage,lastStage]) {
    await page.getByRole('button',{name:'推进下一阶段',exact:true}).first().click();
    await page.getByRole('button',{name:'确认推进',exact:true}).click();
    await page.getByText('阶段资料 · '+name,{exact:true}).waitFor();
  }
  await page.getByText('本阶段无需提交材料，向后推进没有材料拦截',{exact:true}).waitFor();
  await page.getByRole('button',{name:'按需补充本阶段附件',exact:true}).first().waitFor();
  assert.deepEqual(await ok(`/project/${project.id}/configuration`),snap);
  const revised=await ok(`/project/config/types/${created.id}/definition`);
  await ok(`/project/config/types/${created.id}/definition`,'PUT',{name:typeName+'变更',position:revised.position,expectedEpoch:revised.expectedEpoch,requestId:crypto.randomUUID(),phases:[...revised.phases].reverse()});
  assert.deepEqual(await ok(`/project/${project.id}/configuration`),snap,'Changing current configuration cannot alter an existing project snapshot');
  const newerProject=await ok('/project','POST',{name:'新配置隔离项目',category:'OFFLINE',typeId:created.id,ownerId:1});
  assert.equal(newerProject.phaseId,snap.phases[2].phaseId);
  await page.reload();await page.getByText('类型：'+typeName,{exact:true}).waitFor();
  const legacyType=await ok('/project/config/types','POST',{name:'旧接口隔离类型-'+Date.now()});
  await ok(`/project/config/types/${legacyType.id}/phases`,'PUT',[101,102]);
  await ok(`/project/config/types/${legacyType.id}/phases/101/templates`,'PUT',[{templateId:101,required:0,sort:1}]);
  await ok(`/project/config/types/${legacyType.id}`,'PUT',{name:'旧接口维护完成-'+Date.now(),sort:1});
  const legacyDefinition=await ok(`/project/config/types/${legacyType.id}/definition`);
  assert.equal(legacyDefinition.phases[0].mode,'CUSTOM');
  const legacyProject=await ok('/project','POST',{name:'旧接口兼容项目',category:'OFFLINE',typeId:legacyType.id,ownerId:1});
  await ok(`/project/${legacyProject.id}/phase`,'PUT',{phaseId:102});
  assert.notEqual((await api(context.request,`/project/config/types/${legacyType.id}/phases/101/templates`,'PUT',[])).code,0);
  assert.notEqual((await api(context.request,`/project/config/types/${legacyType.id}/phases`,'PUT',[])).code,0);
  const finalTypes=await ok('/project/config/types');assert.deepEqual(finalTypes.map(t=>t.sort),finalTypes.map((_,i)=>i+1));
  const outsider = await browser.newContext();
  try {
    const login = await api(outsider.request, "/auth/login", "POST", {
      username: "配置验收普通员工",
      password: process.env.PC_TEST_PASSWORD,
    });
    assert.equal(login.code, 0);
    for(const [path,method,data] of [
      ['/project/config/types','POST',{name:'越权类型'}],
      ['/project/config/phases','POST',{name:'越权阶段'}],
      ['/project/config/phases/101','PUT',{name:'越权更名'}],
      ['/project/config/phases/101','DELETE',undefined],
      ['/project/config/templates','POST',{name:'越权模板',templateType:'FILE'}],
      ['/project/config/templates/101','DELETE',undefined],
    ])assert.equal((await api(outsider.request,path,method,data)).code,403,method+' '+path);

    assert.equal(
      (await api(outsider.request, "/project/config/definition-context")).code,
      403,
    );
    assert.equal(
      (await api(outsider.request, `/project/${project.id}/configuration`))
        .code,
      403,
    );
    assert.equal(
      (
        await api(outsider.request, "/project/config/definitions", "POST", {
          ...definition,
          requestId: crypto.randomUUID(),
        })
      ).code,
      403,
    );
  } finally {
    await outsider.close();
  }
  await page.goto(base+"/admin/project/config");
  await page.getByText("表格视图", { exact: true }).click();
  await page.getByRole("tab", { name: "项目类型" }).waitFor();
  assert.deepEqual(errors, []);
  await writeFile(
    new URL("live-result.json", artifact),
    JSON.stringify(
      {
        result: "PASS",
        environment:
          "isolated Edge + Vite + Spring + MySQL 13317/pc_acceptance",
        checks: [
          "stage draft flow preview toggle",
          "legacy type/flow/binding API compatibility",
          "legacy empty requests rejected without changing semantics",
          "legacy dictionary writes deny ordinary users",
          "real stage material upload and two phase advances",
          "current stage view follows advancement",
          "NONE keeps optional upload available",
          "old snapshot unchanged after configuration publish",
          "new projects use new phase sequence",
          "real login",
          "draft-only stage creation",
          "three material modes",
          "employee preview",
          "single atomic save",
          "edit reload",
          "unique rank move",
          "cancel without writes",
          "project snapshot read",
          "anonymous and ordinary-user denial",
          "table view",
        ],
        pageErrors: errors,
        configurationMutations: mutations,
      },
      null,
      2,
    ),
  );
  console.log(
    "PASS: real isolated project configuration UI/API/DB flows; no API mocks; no production requests.",
  );
} catch (e) {
  await page.screenshot({
    path: new URL("failure.png", artifact).pathname.replace(
      /^\/([A-Za-z]:)/,
      "$1",
    ),
    fullPage: true,
  });
  console.error(
    "URL",
    page.url(),
    "PAGE_ERRORS",
    errors,
    "VISIBLE_TEXT",
    (await page.locator("body").innerText()).slice(-2400),
  );
  throw e;
} finally {
  await browser.close();
}
