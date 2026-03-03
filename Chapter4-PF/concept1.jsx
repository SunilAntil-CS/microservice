import { useState } from "react";

// ─────────────────────────────────────────────
// SHARED CONSTANTS
// ─────────────────────────────────────────────
const LC = {
  bss:      { bg:"#0f2240", border:"#3b82f6", text:"#93c5fd", label:"BSS Layer"   },
  mediation:{ bg:"#1e0f35", border:"#a855f7", text:"#d8b4fe", label:"Mediation"   },
  nss:      { bg:"#0a1f15", border:"#22c55e", text:"#86efac", label:"NSS Layer"   },
  special:  { bg:"#1a0808", border:"#ef4444", text:"#fca5a5", label:"Special"     },
  db:       { bg:"#071a25", border:"#06b6d4", text:"#67e8f9", label:"Data Store"  },
};

const MS = {
  request:   "#60a5fa", response:  "#34d399", event:     "#a78bfa",
  self:      "#fbbf24", danger:    "#f87171", crash:     "#ef4444",
  success:   "#4ade80", outbox:    "#f59e0b", atomic:    "#06b6d4",
  retry:     "#fb923c", conflict:  "#fb923c", lock:      "#f59e0b",
  compensate:"#c084fc", corrective:"#f43f5e", version:   "#06b6d4",
  precheck:  "#fb923c",
};

const DASH = { response:true, conflict:true, retry:true, abandoned:true };

const TC = {
  T1:"#3b82f6",T2:"#a855f7",T3:"#f59e0b",T4:"#ef4444",
  T4a:"#ef4444",T4b:"#ef4444",T4c:"#ef4444",T5:"#10b981",T6:"#06b6d4",
};

// ─────────────────────────────────────────────
// ALL CONCEPT DATA
// ─────────────────────────────────────────────
export const CONCEPTS = [
  {
    id:"c1", title:"Semantic Lock",
    subtitle:"Lost Update & Business Integrity Prevention",
    color:"#3b82f6", icon:"🔒",
    actors:{
      customer:  { label:"Customer App",  layer:"bss"      },
      crm:       { label:"CRM",           layer:"bss"      },
      mediation: { label:"Mediation",     layer:"mediation"},
      aaa:       { label:"AAA Server",    layer:"nss"      },
      suspension:{ label:"Suspension Job",layer:"special"  },
      bng:       { label:"BNG Router",    layer:"nss"      },
    },
    scenarios:{
      without:{
        label:"❌ Without Lock", color:"#ef4444",
        desc:"Lost Update — suspended user enjoys 150Mbps, no refund ever triggered",
        steps:[
          {id:1,from:"customer",  to:"crm",       msg:"Buy Booster (50GB/150Mbps)",        type:"request",  time:"T1",note:"Payment collected by CRM"},
          {id:2,from:"crm",       to:"crm",        msg:"Collects Payment ✅",               type:"self",     time:"T1"},
          {id:3,from:"crm",       to:"mediation",  msg:"BoosterPurchaseEvent → Kafka",      type:"event",    time:"T1"},
          {id:4,from:"mediation", to:"aaa",        msg:"ApplyBoosterCommand",               type:"request",  time:"T1"},
          {id:5,from:"aaa",       to:"bng",        msg:"CoA → Upgrade to 150Mbps",         type:"request",  time:"T2",note:"No lock set — concurrent operations possible!"},
          {id:6,from:"suspension",to:"aaa",        msg:"Read subscriber state",             type:"request",  time:"T2",note:"Midnight suspension job runs concurrently"},
          {id:7,from:"aaa",       to:"suspension", msg:"state = ACTIVE (no lock visible!)", type:"response", time:"T2"},
          {id:8,from:"suspension",to:"aaa",        msg:"SET state = SUSPENDED ❌",          type:"danger",   time:"T2",note:"Overwrites silently — no conflict detected"},
          {id:9,from:"bng",       to:"aaa",        msg:"CoA-ACK ✅ 150Mbps on hardware",   type:"response", time:"T3"},
          {id:10,from:"aaa",      to:"aaa",        msg:"SET state = ACTIVE\n← overwrites SUSPENDED!",type:"danger",time:"T3",note:"LOST UPDATE — silent corruption forever!"},
          {id:11,from:"aaa",      to:"mediation",  msg:"BoosterSuccessEvent",              type:"event",    time:"T3"},
          {id:12,from:"mediation",to:"crm",        msg:"Booster Activated",                type:"danger",   time:"T3"},
          {id:13,from:"crm",      to:"customer",   msg:'"Booster Active!" ❌ (user suspended!)',type:"danger",time:"T3",note:"No refund. User on suspended account at 150Mbps."},
        ]
      },
      booster_first:{
        label:"✅ Booster First", color:"#22c55e",
        desc:"Lock → suspension 409 → retries → pivot decision → compensation",
        steps:[
          {id:1,from:"customer",  to:"crm",       msg:"Buy Booster",                       type:"request",  time:"T1"},
          {id:2,from:"crm",       to:"crm",        msg:"Collects Payment ✅",               type:"self",     time:"T1"},
          {id:3,from:"crm",       to:"mediation",  msg:"BoosterPurchaseEvent → Kafka",      type:"event",    time:"T1"},
          {id:4,from:"mediation", to:"aaa",        msg:"ApplyBoosterCommand",               type:"request",  time:"T1"},
          {id:5,from:"aaa",       to:"aaa",        msg:"PRE-CHECK: ACTIVE ✅\nSET UPGRADE_IN_PROGRESS\nlockAcquiredAt=now()", type:"lock",time:"T1",note:"Semantic lock acquired atomically in MongoDB"},
          {id:6,from:"aaa",       to:"bng",        msg:"CoA → Upgrade to 150Mbps",         type:"request",  time:"T2"},
          {id:7,from:"suspension",to:"aaa",        msg:"PRE-CHECK: Read state",             type:"request",  time:"T2",note:"Suspension job runs concurrently"},
          {id:8,from:"aaa",       to:"suspension", msg:"409 Conflict — UPGRADE_IN_PROGRESS",type:"conflict", time:"T2",note:"Suspension backs off — retries later"},
          {id:9,from:"bng",       to:"aaa",        msg:"CoA-ACK ✅ 150Mbps confirmed",     type:"response", time:"T3",note:"PIVOT POINT — hardware confirmed"},
          {id:10,from:"aaa",      to:"aaa",        msg:"@Version ✅ → ACTIVE\nLock released",type:"success",time:"T3"},
          {id:11,from:"aaa",      to:"mediation",  msg:"BoosterSuccessEvent [Pessimistic View]",type:"event",time:"T3",note:"Notify ONLY after hardware ACK"},
          {id:12,from:"mediation",to:"crm",        msg:"BoosterActivated ✅",               type:"success",  time:"T3"},
          {id:13,from:"crm",      to:"customer",   msg:'"Booster Active! 150Mbps" ✅',     type:"success",  time:"T3"},
          {id:14,from:"suspension",to:"aaa",       msg:"RETRY — Read state",               type:"request",  time:"T4",note:"Retries after lock released"},
          {id:15,from:"aaa",      to:"suspension", msg:"state = ACTIVE",                   type:"response", time:"T4"},
          {id:16,from:"suspension",to:"suspension",msg:"PIVOT: Unpaid bill exists\nSuspension wins over booster",type:"self",time:"T4",note:"Pivot Transaction — business rule decides"},
          {id:17,from:"suspension",to:"aaa",       msg:"SET state = SUSPENDED",            type:"request",  time:"T4"},
          {id:18,from:"aaa",      to:"bng",        msg:"CoA → Suspension Policy (0Mbps)", type:"request",  time:"T4a",note:"DB change alone ≠ hardware change — CoA required!"},
          {id:19,from:"bng",      to:"aaa",        msg:"CoA-ACK ✅ Suspension on hardware",type:"response", time:"T4b",note:"Hardware confirmed suspended"},
          {id:20,from:"aaa",      to:"mediation",  msg:"HardwareUpgradeConflictEvent",     type:"event",    time:"T4c",note:"Pessimistic View — after hardware confirms"},
          {id:21,from:"mediation",to:"mediation",  msg:"Translate NSS → BSS Command",      type:"self",     time:"T5",note:"Anti-Corruption Layer"},
          {id:22,from:"mediation",to:"crm",        msg:"BusinessConflictCommand",           type:"request",  time:"T5"},
          {id:23,from:"crm",      to:"crm",        msg:"Compensating Tx: Refund payment",  type:"compensate",time:"T5",note:"Compensating Transaction"},
          {id:24,from:"crm",      to:"customer",   msg:"SMS: Suspended. Booster refunded ✅",type:"success",time:"T5"},
        ]
      },
      suspension_first:{
        label:"✅ Suspension First", color:"#f59e0b",
        desc:"Pre-check catches SUSPENDED → fail fast → no CoA → clean refund",
        steps:[
          {id:1,from:"customer",  to:"crm",       msg:"Buy Booster",                       type:"request",  time:"T1"},
          {id:2,from:"crm",       to:"crm",        msg:"Collects Payment ✅",               type:"self",     time:"T1"},
          {id:3,from:"suspension",to:"aaa",        msg:"PRE-CHECK: ACTIVE ✅",              type:"request",  time:"T1",note:"Suspension job runs FIRST"},
          {id:4,from:"suspension",to:"aaa",        msg:"SET state = SUSPENDED",             type:"request",  time:"T1"},
          {id:5,from:"aaa",       to:"bng",        msg:"CoA → Suspension Policy (0Mbps)", type:"request",  time:"T1"},
          {id:6,from:"bng",       to:"aaa",        msg:"CoA-ACK ✅ Hardware suspended",    type:"response", time:"T1",note:"Suspension fully complete with hardware confirmation"},
          {id:7,from:"crm",       to:"mediation",  msg:"BoosterPurchaseEvent → Kafka",      type:"event",    time:"T2"},
          {id:8,from:"mediation", to:"aaa",        msg:"ApplyBoosterCommand",               type:"request",  time:"T2"},
          {id:9,from:"aaa",       to:"aaa",        msg:"PRE-CHECK #1: SUSPENDED ❌\nTerminal state — fail fast!\nDo NOT send CoA to BNG",type:"precheck",time:"T2",note:"Pre-check catches terminal state BEFORE acquiring lock"},
          {id:10,from:"aaa",      to:"aaa",        msg:"Publish BoosterFailedEvent",        type:"event",    time:"T2",note:"No lock acquired. No CoA sent. Zero hardware confusion."},
          {id:11,from:"aaa",      to:"mediation",  msg:"BoosterFailedEvent → Kafka",        type:"event",    time:"T2"},
          {id:12,from:"mediation",to:"crm",        msg:"BusinessFailureCommand",            type:"request",  time:"T2"},
          {id:13,from:"crm",      to:"crm",        msg:"Compensating Tx: Refund",           type:"compensate",time:"T2"},
          {id:14,from:"crm",      to:"customer",   msg:"SMS: Suspended. Booster refunded ✅",type:"success",time:"T2",note:"Clean resolution — no hardware confusion"},
        ]
      },
    }
  },
  {
    id:"c2", title:"Selective Outbox",
    subtitle:"Pod Crash CoA Delivery Guarantee — Flow 1 Only",
    color:"#f59e0b", icon:"📦",
    actors:{
      mediation:{ label:"Mediation",    layer:"mediation"},
      aaa:      { label:"AAA Server",   layer:"nss"     },
      outbox:   { label:"Outbox Relay", layer:"nss"     },
      mongo:    { label:"MongoDB",      layer:"db"      },
      bng:      { label:"BNG Router",   layer:"nss"     },
      kafka:    { label:"Kafka",        layer:"nss"     },
      crm:      { label:"CRM",          layer:"bss"     },
    },
    scenarios:{
      crash_c:{
        label:"❌ Crash C — Worst Case", color:"#ef4444",
        desc:"After CoA-ACK before DB commit — BNG=150Mbps but DB=FAILED → wrong refund",
        steps:[
          {id:1,from:"mediation",to:"aaa",   msg:"ApplyBoosterCommand",                   type:"request", time:"T1"},
          {id:2,from:"aaa",      to:"mongo",  msg:"SET UPGRADE_IN_PROGRESS",              type:"atomic",  time:"T1"},
          {id:3,from:"aaa",      to:"bng",    msg:"CoA → Upgrade to 150Mbps",            type:"request", time:"T2"},
          {id:4,from:"bng",      to:"aaa",    msg:"CoA-ACK ✅ 150Mbps on hardware!",      type:"response",time:"T2",note:"BNG IS upgraded — hardware confirmed"},
          {id:5,from:"aaa",      to:"aaa",    msg:"💥 POD CRASHES\nAFTER CoA-ACK\nBEFORE MongoDB commit",type:"crash",time:"T2",note:"MOST DANGEROUS scenario — hardware diverges from DB"},
          {id:6,from:"aaa",      to:"aaa",    msg:"Watchdog fires 5 mins\nSET state = FAILED ❌",type:"self",time:"T3",note:"Watchdog CANNOT distinguish — blindly marks FAILED"},
          {id:7,from:"aaa",      to:"kafka",  msg:"BoosterFailedEvent",                   type:"danger",  time:"T3"},
          {id:8,from:"kafka",    to:"crm",    msg:"Refund triggered ❌",                  type:"danger",  time:"T3",note:"USER REFUNDED FOR ACTIVE PLAN! Silent corruption!"},
          {id:9,from:"bng",      to:"bng",    msg:"Still at 150Mbps ❌\nNetwork ≠ Database",type:"danger",time:"T3",note:"Hardware state diverged permanently"},
        ]
      },
      with_outbox:{
        label:"✅ With Outbox Pattern", color:"#22c55e",
        desc:"Atomic write → pod crash anywhere → relay recovers → always correct",
        steps:[
          {id:1,from:"mediation",to:"aaa",   msg:"ApplyBoosterCommand",                   type:"request", time:"T1",note:"Command received from Mediation Layer"},
          {id:2,from:"aaa",      to:"mongo",  msg:"ATOMIC TRANSACTION:\n① SET UPGRADE_IN_PROGRESS\n② outbox_events {PENDING, coaPayload}",type:"atomic",time:"T1",note:"Both written atomically — cannot partially fail!"},
          {id:3,from:"mongo",    to:"aaa",    msg:"All three committed atomically ✅",     type:"response",time:"T1"},
          {id:4,from:"aaa",      to:"aaa",    msg:"💥 POD CRASHES — any point after T1",  type:"crash",   time:"T2",note:"Doesn't matter WHEN crash happens!"},
          {id:5,from:"mongo",    to:"mongo",  msg:"outbox_events: PENDING ✅\nSurvives crash independently",type:"success",time:"T2",note:"MongoDB persists regardless of pod lifecycle"},
          {id:6,from:"aaa",      to:"aaa",    msg:"K8s restarts crashed pod",             type:"self",    time:"T3"},
          {id:7,from:"outbox",   to:"mongo",  msg:"Poll: status = PENDING (every 2s)",    type:"outbox",  time:"T3",note:"Relay picks up surviving row"},
          {id:8,from:"mongo",    to:"outbox", msg:"PENDING row returned ✅",              type:"response",time:"T3"},
          {id:9,from:"outbox",   to:"bng",    msg:"CoA → 150Mbps (retry from DB state)", type:"outbox",  time:"T3",note:"CoA sent from known persistent state"},
          {id:10,from:"bng",     to:"outbox", msg:"CoA-ACK ✅",                          type:"response",time:"T4",note:"Hardware confirmed — pivot point"},
          {id:11,from:"outbox",  to:"mongo",  msg:"status=SENT\nsubscriber=ACTIVE @Version ✅",type:"success",time:"T4"},
          {id:12,from:"outbox",  to:"kafka",  msg:"BoosterSuccessEvent [Pessimistic View]",type:"event",  time:"T4",note:"Notify ONLY after hardware ACK"},
          {id:13,from:"kafka",   to:"crm",    msg:"Booster Activated via Mediation ✅",   type:"success", time:"T4"},
        ]
      },
      watchdog_coord:{
        label:"Watchdog + Outbox Coord", color:"#06b6d4",
        desc:"Watchdog marks ABANDONED — prevents stale CoA when relay itself crashes",
        steps:[
          {id:1,from:"aaa",     to:"mongo",  msg:"ATOMIC: UPGRADE_IN_PROGRESS\n+ outbox PENDING",type:"atomic",time:"T1"},
          {id:2,from:"outbox",  to:"outbox", msg:"💥 Outbox relay pod crashes",          type:"crash",   time:"T2",note:"Relay crashes separately from AAA pod"},
          {id:3,from:"mongo",   to:"mongo",  msg:"subscriber: UPGRADE_IN_PROGRESS\noutbox: PENDING — both stuck",type:"danger",time:"T2"},
          {id:4,from:"aaa",     to:"aaa",    msg:"Watchdog: 5 min\nSET subscriber = FAILED",type:"self",  time:"T3",note:"Watchdog clears subscriber lock"},
          {id:5,from:"aaa",     to:"mongo",  msg:"UPDATE outbox SET status = ABANDONED\n(where status = PENDING)",type:"success",time:"T3",note:"CRITICAL — watchdog also abandons outbox rows!"},
          {id:6,from:"mongo",   to:"aaa",    msg:"Both updated ✅",                      type:"response",time:"T3"},
          {id:7,from:"aaa",     to:"aaa",    msg:"New outbox relay pod starts",          type:"self",    time:"T4",note:"K8s restarts relay pod"},
          {id:8,from:"outbox",  to:"mongo",  msg:"Poll: status = PENDING",               type:"outbox",  time:"T4"},
          {id:9,from:"mongo",   to:"outbox", msg:"No PENDING rows ✅\n(ABANDONED ignored)",type:"success",time:"T4",note:"Stale CoA NOT sent — race condition prevented!"},
          {id:10,from:"outbox", to:"kafka",  msg:"BoosterFailedEvent",                   type:"event",   time:"T4"},
          {id:11,from:"kafka",  to:"crm",    msg:"Refund via Mediation ✅",              type:"success", time:"T4"},
        ]
      },
    }
  },
  {
    id:"c3", title:"Optimistic Locking + Corrective CoA",
    subtitle:"GR Replication Lag — Version Conflict Recovery",
    color:"#06b6d4", icon:"⚡",
    actors:{
      admin:    { label:"Admin/CRM",    layer:"bss"      },
      mediation:{ label:"Mediation",    layer:"mediation"},
      mumbai:   { label:"Mumbai PCF",   layer:"nss"      },
      gujarat:  { label:"Gujarat PCF",  layer:"nss"      },
      mongo:    { label:"MongoDB GR",   layer:"db"       },
      bng:      { label:"BNG Router",   layer:"nss"      },
    },
    scenarios:{
      without_version:{
        label:"❌ Without @Version", color:"#ef4444",
        desc:"Dirty Write — admin emergency downgrade silently lost!",
        steps:[
          {id:1,from:"gujarat",  to:"bng",     msg:"CoA → 150Mbps (@Version=5)",           type:"request", time:"T1",note:"Gujarat PCF sends upgrade, holds version=5"},
          {id:2,from:"admin",    to:"mediation",msg:"Emergency downgrade → 100Mbps",        type:"request", time:"T2",note:"Admin changes plan during CoA window"},
          {id:3,from:"mediation",to:"mumbai",  msg:"DowngradeCommand → 100Mbps",            type:"request", time:"T2"},
          {id:4,from:"mumbai",   to:"mongo",   msg:"SAVE: @Version=6, speed=100Mbps",       type:"version", time:"T2",note:"Mumbai saves admin's change"},
          {id:5,from:"mongo",    to:"gujarat", msg:"Replicates @Version=6 (20-40ms lag)",   type:"response",time:"T2",note:"GR replication lag — Gujarat gets update"},
          {id:6,from:"bng",      to:"gujarat", msg:"CoA-ACK ✅ 150Mbps on hardware",        type:"response",time:"T3"},
          {id:7,from:"gujarat",  to:"mongo",   msg:"SAVE: @Version=5, speed=150Mbps\n← BLINDLY OVERWRITES!",type:"danger",time:"T3",note:"Gujarat overwrites admin's version=6!"},
          {id:8,from:"mongo",    to:"gujarat", msg:"Saved ✅ (corruption — no check!)",     type:"danger",  time:"T3"},
          {id:9,from:"gujarat",  to:"gujarat", msg:"Admin's 100Mbps LOST ❌\nSubscriber at 150Mbps forever\nNo error raised",type:"danger",time:"T3",note:"Silent dirty write — no one knows!"},
        ]
      },
      with_version:{
        label:"✅ State-First + Corrective CoA", color:"#06b6d4",
        desc:"State=ACTIVE + speed mismatch → corrective CoA preserves admin intent",
        steps:[
          {id:1,from:"gujarat",  to:"bng",     msg:"CoA → 150Mbps (@Version=5)",           type:"request", time:"T1",note:"Gujarat sends upgrade, holds version=5"},
          {id:2,from:"admin",    to:"mediation",msg:"Emergency downgrade → 100Mbps",        type:"request", time:"T2"},
          {id:3,from:"mediation",to:"mumbai",  msg:"DowngradeCommand",                      type:"request", time:"T2"},
          {id:4,from:"mumbai",   to:"mongo",   msg:"SAVE: @Version=6, speed=100Mbps",       type:"version", time:"T2",note:"Mumbai increments to version=6"},
          {id:5,from:"mongo",    to:"gujarat", msg:"Replicates @Version=6",                 type:"response",time:"T2"},
          {id:6,from:"bng",      to:"gujarat", msg:"CoA-ACK ✅ 150Mbps on hardware",        type:"response",time:"T3",note:"BNG IS upgraded to 150Mbps"},
          {id:7,from:"gujarat",  to:"mongo",   msg:"SAVE @Version=5 → conflict check",      type:"version", time:"T3"},
          {id:8,from:"mongo",    to:"gujarat", msg:"OptimisticLockingException! ❌\nDB=6, Sent=5",type:"conflict",time:"T3",note:"Version mismatch — admin's change preserved!"},
          {id:9,from:"gujarat",  to:"mongo",   msg:"READ fresh state",                      type:"request", time:"T3"},
          {id:10,from:"mongo",   to:"gujarat", msg:"Fresh: @Version=6, speed=100Mbps",      type:"response",time:"T3",note:"Admin's intended speed = 100Mbps"},
          {id:11,from:"gujarat", to:"gujarat", msg:"STATE-FIRST CHECK:\nFresh state=ACTIVE ✅\nSpeed: 150≠100Mbps → mismatch\n→ Send corrective CoA",type:"success",time:"T3",note:"State=ACTIVE confirms admin changed plan — not OCS throttle. Safe to reconcile hardware with admin intent."},
          {id:12,from:"gujarat", to:"bng",     msg:"CORRECTIVE CoA → 100Mbps\n(align hardware with admin intent)",type:"corrective",time:"T4",note:"Corrective CoA realigns hardware"},
          {id:13,from:"bng",     to:"gujarat", msg:"CoA-ACK ✅ 100Mbps applied",            type:"response",time:"T4"},
          {id:14,from:"gujarat", to:"mongo",   msg:"SAVE: @Version=7, speed=100Mbps ✅",    type:"success", time:"T4",note:"Network state = Database state ✅"},
          {id:15,from:"gujarat", to:"gujarat", msg:"Admin's change preserved ✅\nNetwork = DB = 100Mbps",type:"success",time:"T4"},
        ]
      },
      fup_bypass_risk:{
        label:"❌ Speed-Only Check — FUP Bypass Risk", color:"#ef4444",
        desc:"Speed-only check ignores QUOTA_EXHAUSTED — OCS throttle bypassed, state corrupted, revenue leakage",
        steps:[
          {id:1,from:"mongo",   to:"mongo",  msg:"OCS: quota exhausted\nstate=QUOTA_EXHAUSTED\nspeed=1Mbps @Version=6",type:"danger",  time:"T1",note:"OCS sets QUOTA_EXHAUSTED in MongoDB while Gujarat's CoA is in-flight — GR lag 20–40ms means Gujarat didn't see it"},
          {id:2,from:"gujarat", to:"bng",    msg:"CoA → 150Mbps (@Version=5)\nBooster in-flight",                     type:"request", time:"T1",note:"Gujarat's CoA was dispatched before OCS update arrived — valid GR replication race"},
          {id:3,from:"bng",     to:"gujarat",msg:"CoA-ACK ✅ 150Mbps on hardware",                                    type:"response",time:"T2"},
          {id:4,from:"gujarat", to:"mongo",  msg:"SAVE @Version=5 → conflict",                                        type:"version", time:"T2"},
          {id:5,from:"mongo",   to:"gujarat",msg:"OptimisticLockingException ❌\nDB=6, Sent=5",                       type:"conflict",time:"T2",note:"OCS saved QUOTA_EXHAUSTED at version=6 — conflict correctly detected"},
          {id:6,from:"gujarat", to:"mongo",  msg:"READ fresh state",                                                  type:"request", time:"T2"},
          {id:7,from:"mongo",   to:"gujarat",msg:"Fresh: QUOTA_EXHAUSTED\nspeed=1Mbps @Version=6",                    type:"response",time:"T2",note:"OCS decision is visible — subscriber's quota is exhausted"},
          {id:8,from:"gujarat", to:"gujarat",msg:"SPEED-ONLY CHECK ❌\nfresh=1Mbps ≠ CoA=150Mbps\n→ 'Speed mismatch! Send corrective!'",type:"danger",time:"T3",note:"Bug: speed-only check ignores QUOTA_EXHAUSTED state — treats OCS throttle same as admin plan change"},
          {id:9,from:"gujarat", to:"bng",    msg:"CORRECTIVE CoA → 1Mbps ❌\n(treating OCS as admin change)",         type:"danger",  time:"T3",note:"AAA usurps OCS authority — should NEVER send corrective CoA when fresh state=QUOTA_EXHAUSTED"},
          {id:10,from:"bng",    to:"gujarat",msg:"CoA-ACK ✅ 1Mbps",                                                  type:"response",time:"T3"},
          {id:11,from:"gujarat",to:"mongo",  msg:"SAVE: state=ACTIVE ❌\n@Version=7, speed=1Mbps",                    type:"danger",  time:"T3",note:"QUOTA_EXHAUSTED overwritten with ACTIVE — OCS enforcement broken for next accounting cycle"},
          {id:12,from:"gujarat",to:"gujarat",msg:"OCS THROTTLE BYPASSED ❌\nState: ACTIVE (should be QUOTA_EXHAUSTED)\nNext RADIUS: no FUP enforcement",type:"danger",time:"T3",note:"Revenue leakage. OCS quota decision silently lost. AAA Server must check state first — OCS is the quota authority, not speed comparator."},
        ]
      },
      no_conflict:{
        label:"✅ State-First: ACTIVE + Speed Match", color:"#22c55e",
        desc:"State=ACTIVE + speed match → re-save only; QUOTA_EXHAUSTED → OCS decision stands, no corrective CoA",
        steps:[
          {id:1,from:"gujarat",  to:"bng",     msg:"CoA → 150Mbps (@Version=5)",           type:"request", time:"T1"},
          {id:2,from:"mumbai",   to:"mongo",   msg:"Unrelated update @Version=6\n(quota, state — speed unchanged)",type:"version",time:"T2",note:"Different field updated — not speed"},
          {id:3,from:"bng",      to:"gujarat", msg:"CoA-ACK ✅",                            type:"response",time:"T3"},
          {id:4,from:"gujarat",  to:"mongo",   msg:"SAVE @Version=5 → conflict!",           type:"version", time:"T3"},
          {id:5,from:"mongo",    to:"gujarat", msg:"OptimisticLockingException",            type:"conflict",time:"T3"},
          {id:6,from:"gujarat",  to:"mongo",   msg:"READ fresh state",                      type:"request", time:"T3"},
          {id:7,from:"mongo",    to:"gujarat", msg:"Fresh: @Version=6, speed=150Mbps ✅",   type:"response",time:"T3",note:"Speed matches what we told BNG!"},
          {id:8,from:"gujarat",  to:"gujarat", msg:"STATE-FIRST CHECK:\nFresh state=ACTIVE ✅\nSpeed: 150=150Mbps → match\n→ Re-save only, no CoA",type:"success",time:"T3",note:"State=ACTIVE + speed match = no hardware change needed. If state were QUOTA_EXHAUSTED → OCS decision stands, no corrective CoA ever."},
          {id:9,from:"gujarat",  to:"mongo",   msg:"SAVE @Version=7, ACTIVE ✅",            type:"success", time:"T4",note:"Re-save only — no CoA needed"},
        ]
      },
    }
  },
  {
    id:"c4", title:"Pessimistic View",
    subtitle:"No False Success — Hardware Must Confirm Before Notify",
    color:"#a855f7", icon:"👁️",
    actors:{
      customer: { label:"Customer App",    layer:"bss"      },
      crm:      { label:"CRM",             layer:"bss"      },
      mediation:{ label:"Mediation",       layer:"mediation"},
      aaa:      { label:"AAA Server",      layer:"nss"      },
      bng:      { label:"BNG Router",      layer:"nss"      },
      notif:    { label:"Notification Svc",layer:"bss"      },
    },
    scenarios:{
      optimistic_wrong:{
        label:"❌ Optimistic (Wrong)", color:"#ef4444",
        desc:"Notify before hardware — app shows success but user gets slow speed",
        steps:[
          {id:1,from:"customer",  to:"crm",      msg:"Buy Booster",                       type:"request", time:"T1"},
          {id:2,from:"crm",       to:"mediation", msg:"BoosterPurchaseEvent → Kafka",      type:"event",   time:"T1"},
          {id:3,from:"mediation", to:"aaa",       msg:"ApplyBoosterCommand",               type:"request", time:"T1"},
          {id:4,from:"aaa",       to:"crm",       msg:"'Success!' immediately ❌",         type:"danger",  time:"T1",note:"Notifies BEFORE hardware confirms — optimistic!"},
          {id:5,from:"crm",       to:"customer",  msg:'"Booster Active! 150Mbps" ❌',     type:"danger",  time:"T1",note:"User sees success immediately on app"},
          {id:6,from:"aaa",       to:"bng",       msg:"CoA → Upgrade to 150Mbps",         type:"request", time:"T2"},
          {id:7,from:"bng",       to:"bng",       msg:"💥 BNG reboots!\nCoA lost in flight",type:"crash",  time:"T2",note:"Router reboot — CoA never applied to hardware"},
          {id:8,from:"aaa",       to:"aaa",       msg:"CoA timeout — no ACK\nSET state = FAILED",type:"danger",time:"T3"},
          {id:9,from:"customer",  to:"customer",  msg:"App: 150Mbps ❌\nActual: 50Mbps ❌\n← #1 cause of complaints",type:"danger",time:"T3",note:"User calls support — app lied to them!"},
        ]
      },
      flow1_booster:{
        label:"✅ Flow 1 — Booster", color:"#22c55e",
        desc:"Processing screen → CoA → ACK → THEN notify — no false success",
        steps:[
          {id:1,from:"customer",  to:"crm",      msg:"Buy Booster",                       type:"request", time:"T1"},
          {id:2,from:"crm",       to:"customer",  msg:"'Processing...' (hidden state)",    type:"response",time:"T1",note:"User waits — no premature success shown"},
          {id:3,from:"crm",       to:"mediation", msg:"BoosterPurchaseEvent → Kafka",      type:"event",   time:"T1"},
          {id:4,from:"mediation", to:"aaa",       msg:"ApplyBoosterCommand",               type:"request", time:"T1"},
          {id:5,from:"aaa",       to:"aaa",       msg:"SET UPGRADE_IN_PROGRESS\n+ Outbox PENDING (atomic)",type:"lock",time:"T1"},
          {id:6,from:"aaa",       to:"bng",       msg:"CoA → Upgrade to 150Mbps",         type:"request", time:"T2"},
          {id:7,from:"bng",       to:"aaa",       msg:"CoA-ACK ✅ 150Mbps confirmed",     type:"response",time:"T2",note:"PIVOT POINT — hardware confirmed"},
          {id:8,from:"aaa",       to:"aaa",       msg:"@Version ✅ → ACTIVE",              type:"success", time:"T2"},
          {id:9,from:"aaa",       to:"mediation", msg:"BoosterSuccessEvent\n[ONLY NOW — Pessimistic View]",type:"event",time:"T2",note:"Hidden until hardware confirms"},
          {id:10,from:"mediation",to:"crm",       msg:"BoosterActivated",                  type:"success", time:"T2"},
          {id:11,from:"crm",      to:"customer",  msg:'"Booster Active! 150Mbps!" ✅',    type:"success", time:"T2",note:"Real confirmed success — hardware already running"},
        ]
      },
      flow2_fup:{
        label:"✅ Flow 2 — FUP Throttle", color:"#f59e0b",
        desc:"Quota exhausted → CoA throttle → ACK → THEN send SMS",
        steps:[
          {id:1,from:"bng",       to:"aaa",       msg:"RADIUS Interim-Update\n(OCS: quota exhausted)",type:"request",time:"T1",note:"OCS signals 100% quota used in accounting response"},
          {id:2,from:"aaa",       to:"aaa",       msg:"Calculate delta ✅\nOCS quota exhausted detected",type:"self",time:"T1"},
          {id:3,from:"aaa",       to:"aaa",       msg:"SET state = QUOTA_EXHAUSTED (MongoDB)",type:"self",time:"T1"},
          {id:4,from:"aaa",       to:"bng",       msg:"CoA Type 2 → FUP throttle 1Mbps",  type:"request", time:"T2",note:"Enforcement CoA — throttle to FUP speed"},
          {id:5,from:"bng",       to:"aaa",       msg:"CoA-ACK ✅ 1Mbps applied on hardware",type:"response",time:"T2",note:"PIVOT POINT — hardware confirmed throttled"},
          {id:6,from:"aaa",       to:"mediation", msg:"ThresholdCrossedEvent (100%)\n[ONLY after CoA-ACK — Pessimistic View]",type:"event",time:"T2",note:"No SMS until hardware confirms throttle"},
          {id:7,from:"mediation", to:"notif",     msg:"SendSMS command",                    type:"request", time:"T2"},
          {id:8,from:"notif",     to:"customer",  msg:"SMS: Data exhausted.\nSpeed reduced to 1Mbps ✅",type:"success",time:"T2",note:"User told AFTER hardware already throttled"},
        ]
      },
    }
  },

  // ─── C6: Reactive Event Loop ───────────────────────────────────────────
  {
    id:"c6", title:"Reactive Event Loop",
    subtitle:"WebFlux Thread Starvation — Blocking Calls Kill Throughput",
    color:"#f43f5e", icon:"⚡",
    actors:{
      radius:  { label:"RADIUS Client",       layer:"bss" },
      netty:   { label:"Netty Event Loop",    layer:"nss" },
      mongoth: { label:"MongoDB Thread Pool", layer:"nss" },
      mongo:   { label:"MongoDB",             layer:"db"  },
    },
    scenarios:{
      blocking:{
        label:"❌ Blocking Mongo Call", color:"#ef4444",
        desc:"Thread.sleep-equivalent inside handler → all 16 Netty threads frozen → full collapse",
        steps:[
          {id:1, from:"radius",  to:"netty",   msg:"RADIUS Packet #1 arrives",                        type:"request", time:"T1", note:"50,000 pkt/sec — handler assigned to Netty thread-1"},
          {id:2, from:"netty",   to:"mongo",   msg:"mongoTemplate.findOne() [BLOCKING]",               type:"danger",  time:"T1", note:"Blocking call — Netty thread-1 is now parked waiting on MongoDB I/O"},
          {id:3, from:"radius",  to:"netty",   msg:"RADIUS Packets #2–16 arrive",                     type:"request", time:"T2", note:"15 more packets — threads 2 through 16 all pick up work"},
          {id:4, from:"netty",   to:"mongo",   msg:"mongoTemplate.findOne() ×15 [BLOCKING]",           type:"danger",  time:"T2", note:"All 16 Netty threads now blocked on MongoDB I/O — zero threads free"},
          {id:5, from:"netty",   to:"netty",   msg:"All 16 threads FROZEN\nEvent loop starved ❌",    type:"crash",   time:"T2", note:"Zero free threads — new packets cannot be accepted by any handler"},
          {id:6, from:"radius",  to:"netty",   msg:"RADIUS Packets #5–N queue up",                    type:"danger",  time:"T3", note:"Packets pile up in socket buffer — no thread to handle them"},
          {id:7, from:"netty",   to:"netty",   msg:"Socket buffer fills → packets DROPPED",            type:"crash",   time:"T3", note:"Kernel drops incoming UDP — RADIUS client sees timeouts, zero ACKs"},
          {id:8, from:"radius",  to:"radius",  msg:"RFC 2865 retransmit storm ×3",                    type:"retry",   time:"T3", note:"Client retransmits make storm worse — positive feedback loop compounds collapse"},
          {id:9, from:"mongo",   to:"netty",   msg:"MongoDB finally responds → thread released",       type:"response",time:"T4", note:"Too late — retransmit storm already in progress, buffers overflowed"},
          {id:10,from:"netty",   to:"radius",  msg:"RADIUS timeout / reject ×N ❌",                   type:"danger",  time:"T4", note:"Subscribers cannot authenticate — full throughput collapse at carrier scale"},
        ]
      },
      reactive:{
        label:"✅ Reactive Mongo", color:"#22c55e",
        desc:"ReactiveMongoTemplate → Mono<> returned instantly → thread free → 50K pkt/sec sustained",
        steps:[
          {id:1, from:"radius",  to:"netty",   msg:"RADIUS Packet #1 arrives",                        type:"request", time:"T1", note:"Handler invoked on Netty thread-1"},
          {id:2, from:"netty",   to:"mongoth", msg:"reactiveMongoTemplate.findOne() [NON-BLOCK]",      type:"atomic",  time:"T1", note:"Returns Mono<> immediately — Netty thread-1 is NOT blocked, I/O handed to pool"},
          {id:3, from:"netty",   to:"netty",   msg:"Thread-1 FREE immediately ✅\nReturns to event loop",type:"success",time:"T1",note:"Thread returns to pool in <1ms — ready to handle next packet instantly"},
          {id:4, from:"radius",  to:"netty",   msg:"Packets #2–50,000 flow continuously",              type:"request", time:"T2", note:"All packets served by same 16 threads — no starvation possible"},
          {id:5, from:"mongoth", to:"netty",   msg:"MongoDB response → callback fires",                type:"response",time:"T2", note:"I/O completes → subscriber's Mono resolves on any free Netty thread"},
          {id:6, from:"netty",   to:"netty",   msg:"Continue handler logic with DB result",            type:"self",    time:"T2", note:"Reactive chain continues — flatMap / map operators process response"},
          {id:7, from:"netty",   to:"radius",  msg:"RADIUS ACK — 50,000/sec ✅",                      type:"success", time:"T3", note:"Full throughput sustained — zero dropped packets, zero starvation"},
          {id:8, from:"radius",  to:"netty",   msg:"Traffic spike ×2 → absorbed cleanly",              type:"request", time:"T3", note:"Reactive pipeline back-pressures gracefully rather than dropping packets"},
          {id:9, from:"netty",   to:"netty",   msg:"Thread utilisation: 40–60%\nHeadroom maintained ✅",type:"success",time:"T4",note:"Burst capacity reserved — event loop never saturates under normal load"},
          {id:10,from:"netty",   to:"radius",  msg:"Sustained 50K pkt/sec ✅\nZero blocking calls",   type:"success", time:"T4", note:"Carrier-grade throughput from 16 threads — reactive pipeline is the difference"},
        ]
      },
    }
  },

  // ─── C7: Redis Failover Fallback ───────────────────────────────────────
  {
    id:"c7", title:"Redis Failover Fallback",
    subtitle:"Cache Miss → MongoDB Fallback — Zero Billing Error",
    color:"#f59e0b", icon:"🔄",
    actors:{
      bng:     { label:"BNG Router",    layer:"nss" },
      aaa:     { label:"AAA Server",    layer:"nss" },
      redis:   { label:"Redis Cluster", layer:"db"  },
      mongo:   { label:"MongoDB",       layer:"db"  },
      billing: { label:"OCS / Billing", layer:"bss" },
    },
    scenarios:{
      redis_fail:{
        label:"❌ Redis Down — Data Lost", color:"#ef4444",
        desc:"Redis cluster fails → session state lost → quota defaults to 0 → 10GB overcharge",
        steps:[
          {id:1, from:"bng",     to:"aaa",     msg:"RADIUS Interim-Update\n(quota_used: 8GB / 10GB)",  type:"request", time:"T1", note:"Subscriber at 80% quota — AAA writes session state to Redis only"},
          {id:2, from:"aaa",     to:"redis",   msg:"WRITE: sessionId=X quota_used=8GB",                type:"request", time:"T1", note:"Redis-only write — no MongoDB persistence of quota state"},
          {id:3, from:"redis",   to:"aaa",     msg:"ACK ✅",                                           type:"response",time:"T1"},
          {id:4, from:"redis",   to:"redis",   msg:"💥 Redis Cluster FAILS\nAll nodes unreachable",   type:"crash",   time:"T2", note:"5–30 second outage window — common during rolling upgrades or network partitions"},
          {id:5, from:"bng",     to:"aaa",     msg:"RADIUS Interim-Update #2",                         type:"request", time:"T2", note:"Next accounting packet arrives during Redis outage"},
          {id:6, from:"aaa",     to:"redis",   msg:"GET sessionId=X → quota_used?",                   type:"request", time:"T2", note:"Cache lookup — Redis is down"},
          {id:7, from:"redis",   to:"aaa",     msg:"Connection refused / timeout ❌",                  type:"danger",  time:"T2", note:"No fallback defined — AAA has no session state"},
          {id:8, from:"aaa",     to:"aaa",     msg:"quota_used = 0 (default) ❌\nGhost fresh session", type:"danger",  time:"T2", note:"AAA treats subscriber as fresh — full 10GB appears available"},
          {id:9, from:"aaa",     to:"bng",     msg:"RADIUS ACK — continue session",                   type:"response",time:"T3", note:"No throttle applied — subscriber continues at full speed"},
          {id:10,from:"bng",     to:"bng",     msg:"Subscriber uses 10GB 'free' ❌\n(8GB already used)",type:"danger",time:"T3", note:"Subscriber accumulates 18GB total before end of month"},
          {id:11,from:"aaa",     to:"billing", msg:"CDR: quota_used=10GB ❌",                         type:"danger",  time:"T4", note:"CDR reflects ghost session — no record of pre-crash 8GB usage"},
          {id:12,from:"billing", to:"billing", msg:"Invoice: 10GB charged\nActual: 18GB used ❌",     type:"danger",  time:"T4", note:"Undercharge: operator loses 8GB revenue per affected subscriber"},
        ]
      },
      write_through:{
        label:"✅ Write-Through + Fallback", color:"#22c55e",
        desc:"Write Redis + MongoDB simultaneously → cache-miss fallback → correct quota → zero billing error",
        steps:[
          {id:1, from:"bng",   to:"aaa",     msg:"RADIUS Interim-Update (quota: 8GB / 10GB)",          type:"request", time:"T1", note:"Normal accounting flow"},
          {id:2, from:"aaa",   to:"redis",   msg:"WRITE quota_used=8GB [Primary]",                     type:"request", time:"T1", note:"Write to Redis as primary cache — fast path"},
          {id:3, from:"aaa",   to:"mongo",   msg:"WRITE quota_used=8GB [Write-Through]",               type:"atomic",  time:"T1", note:"Simultaneously persist to MongoDB — durable fallback always current"},
          {id:4, from:"redis", to:"aaa",     msg:"ACK ✅",                                             type:"success", time:"T1"},
          {id:5, from:"mongo", to:"aaa",     msg:"ACK ✅",                                             type:"success", time:"T1", note:"Both stores consistent — write overhead ~2–5ms per interim update"},
          {id:6, from:"redis", to:"redis",   msg:"💥 Redis Cluster FAILS",                            type:"crash",   time:"T2", note:"Outage begins — Redis unreachable"},
          {id:7, from:"bng",   to:"aaa",     msg:"RADIUS Interim-Update #2",                           type:"request", time:"T2"},
          {id:8, from:"aaa",   to:"redis",   msg:"GET quota_used → MISS ❌",                          type:"danger",  time:"T2", note:"Redis down — cache miss detected"},
          {id:9, from:"aaa",   to:"mongo",   msg:"FALLBACK READ: GET quota_used",                      type:"request", time:"T2", note:"Fallback to MongoDB — always accurate from write-through writes"},
          {id:10,from:"mongo", to:"aaa",     msg:"quota_used = 8GB ✅ (from write-through)",           type:"response",time:"T2", note:"Accurate state preserved — no ghost session, no zero default"},
          {id:11,from:"aaa",   to:"aaa",     msg:"delta=1.2GB → total=9.2GB\nThreshold: 90%?",         type:"self",    time:"T3", note:"Quota calculation proceeds correctly with accurate baseline"},
          {id:12,from:"aaa",   to:"bng",     msg:"RADIUS ACK — session continues",                     type:"success", time:"T3", note:"Correct quota enforced — subscriber gets accurate experience"},
          {id:13,from:"aaa",   to:"billing", msg:"CDR: quota_used=9.2GB ✅",                          type:"success", time:"T4", note:"Accurate billing record — zero revenue leakage"},
          {id:14,from:"redis", to:"redis",   msg:"Redis recovers → re-sync from MongoDB ✅",           type:"success", time:"T4", note:"On recovery, cache warms from MongoDB — consistent state restored automatically"},
        ]
      },
    }
  },

  // ─── C8: Commutative Updates ───────────────────────────────────────────
  {
    id:"c8", title:"Commutative Updates",
    subtitle:"RFC 2866 Cumulative Octets + RFC 2869 Gigawords — Delta for OCS",
    color:"#e879f9", icon:"📊",
    actors:{
      bng:   { label:"BNG Router",    layer:"nss" },
      aaa:   { label:"AAA Server",    layer:"nss" },
      redis: { label:"Redis",         layer:"db"  },
      mongo: { label:"MongoDB",       layer:"db"  },
      ocs:   { label:"OCS / Billing", layer:"bss" },
    },
    scenarios:{
      without_gigawords:{
        label:"❌ Without Gigawords", color:"#ef4444",
        desc:"32-bit overflow at 300Mbps wraps every 1.8 min → delta goes negative → billing error",
        steps:[
          {id:1, from:"bng",   to:"aaa",   msg:"RADIUS Interim-Update #1\nAcct-Output-Octets = 3,800,000,000",    type:"request", time:"T1", note:"Subscriber at 300Mbps — 32-bit counter approaching 4GB max (4,294,967,295)"},
          {id:2, from:"aaa",   to:"redis", msg:"GET prevTotal → 3,200,000,000",                                    type:"request", time:"T1"},
          {id:3, from:"aaa",   to:"aaa",   msg:"delta = 3.8B - 3.2B = 600MB ✅",                                  type:"self",    time:"T1", note:"Correct delta — no overflow yet"},
          {id:4, from:"aaa",   to:"ocs",   msg:"Usage delta: +600MB",                                              type:"success", time:"T1"},
          {id:5, from:"bng",   to:"aaa",   msg:"RADIUS Interim-Update #2\nAcct-Output-Octets = 200,000,000 ❌",   type:"request", time:"T2", note:"Counter WRAPPED! 32-bit overflowed past 4,294,967,295 → reset near zero"},
          {id:6, from:"aaa",   to:"redis", msg:"GET prevTotal → 3,800,000,000",                                    type:"request", time:"T2"},
          {id:7, from:"aaa",   to:"aaa",   msg:"delta = 200M - 3,800M\n= -3,600,000,000 ❌ NEGATIVE!",            type:"danger",  time:"T2", note:"Without Gigawords, AAA sees counter went BACKWARDS — delta is negative"},
          {id:8, from:"aaa",   to:"aaa",   msg:"Clamp to 0? Use absolute?\nNo correct answer without RFC 2869 ❌", type:"danger",  time:"T3", note:"Any workaround is wrong — clamping loses real usage, absolute double-counts"},
          {id:9, from:"aaa",   to:"ocs",   msg:"Usage delta: 0 bytes ❌\n(clamped negative)",                      type:"danger",  time:"T3", note:"OCS receives zero — subscriber used ~700MB for free, revenue lost"},
          {id:10,from:"ocs",   to:"ocs",   msg:"Balance unchanged ❌\nSubscriber uses data free",                  type:"danger",  time:"T3", note:"At 300Mbps this happens every 1.8 min — 11× in 20 min interval!"},
        ]
      },
      with_gigawords:{
        label:"✅ RFC 2869 Gigawords", color:"#22c55e",
        desc:"Gigawords count wraps → true cumulative = (Gigawords × 4GB) + Octets → correct delta always",
        steps:[
          {id:1, from:"bng",   to:"aaa",   msg:"RADIUS Interim-Update #1\nOctets=3.8B, Gigawords=0",              type:"request", time:"T1", note:"Cumulative = (0 × 4,294,967,296) + 3,800,000,000 = 3.8GB"},
          {id:2, from:"aaa",   to:"redis", msg:"GET prevTotal",                                                     type:"request", time:"T1"},
          {id:3, from:"redis", to:"aaa",   msg:"prevTotal = 3,200,000,000",                                         type:"response",time:"T1"},
          {id:4, from:"aaa",   to:"aaa",   msg:"trueTotal = (0 × 4.29B) + 3.8B\ndelta = 3.8B - 3.2B = 600MB ✅",  type:"success", time:"T1", note:"Correct delta from Gigawords-aware calculation"},
          {id:5, from:"aaa",   to:"redis", msg:"WRITE prevTotal = 3,800,000,000",                                   type:"request", time:"T1"},
          {id:6, from:"aaa",   to:"mongo", msg:"WRITE prevTotal [write-through]",                                   type:"atomic",  time:"T1", note:"Redis + MongoDB write-through for failover safety"},
          {id:7, from:"aaa",   to:"ocs",   msg:"Usage delta: +600MB ✅",                                           type:"success", time:"T1", note:"OCS: balance = balance - 600MB — commutative, no row lock"},
          {id:8, from:"bng",   to:"aaa",   msg:"RADIUS Interim-Update #2\nOctets=200M, Gigawords=1 ✅",            type:"request", time:"T2", note:"Counter wrapped — but Gigawords incremented from 0 to 1"},
          {id:9, from:"aaa",   to:"redis", msg:"GET prevTotal → 3,800,000,000",                                     type:"request", time:"T2"},
          {id:10,from:"aaa",   to:"aaa",   msg:"trueTotal = (1 × 4.29B) + 200M\n= 4,494,967,296\ndelta = 695MB ✅",type:"success",time:"T2", note:"Gigawords-aware: 4,494,967,296 - 3,800,000,000 = 694,967,296 bytes ≈ 695MB"},
          {id:11,from:"aaa",   to:"ocs",   msg:"Usage delta: +695MB ✅",                                           type:"success", time:"T2", note:"Correct delta despite 32-bit wrap — OCS bills accurately"},
          {id:12,from:"ocs",   to:"ocs",   msg:"balance = balance - 695MB ✅\nCommutative — no row locks",         type:"success", time:"T2", note:"Delta operation is order-independent — OCS processes thousands concurrently"},
        ]
      },
    }
  },

  // ─── C9: By Value — Risk-Based Routing ─────────────────────────────────
  {
    id:"c9", title:"By Value — Risk Routing",
    subtitle:"Prepaid Sync vs Postpaid Async — 10× Throughput at Carrier Scale",
    color:"#8b5cf6", icon:"⚖️",
    actors:{
      bng:   { label:"BNG Router",  layer:"nss" },
      aaa:   { label:"AAA Server",  layer:"nss" },
      mongo: { label:"MongoDB",     layer:"db"  },
      ocs:   { label:"OCS",         layer:"bss" },
      kafka: { label:"Kafka",       layer:"db"  },
      cdr:   { label:"CDR System",  layer:"bss" },
    },
    scenarios:{
      all_sync:{
        label:"❌ All Synchronous", color:"#ef4444",
        desc:"All 50M subscribers → synchronous OCS → bottleneck → system collapse",
        steps:[
          {id:1, from:"bng",   to:"aaa",   msg:"RADIUS Interim-Update\n(Postpaid subscriber #1)",             type:"request", time:"T1", note:"Postpaid user — low risk, no real-time quota enforcement needed"},
          {id:2, from:"aaa",   to:"ocs",   msg:"Sync RADIUS proxy → OCS\nWait for response…",                type:"request", time:"T1", note:"AAA blocks waiting for OCS response — thread held for 50–200ms"},
          {id:3, from:"bng",   to:"aaa",   msg:"RADIUS Interim-Update\n(Postpaid subscriber #2)",             type:"request", time:"T1", note:"Second postpaid user — also sent synchronously"},
          {id:4, from:"aaa",   to:"ocs",   msg:"Sync RADIUS proxy → OCS\nWait for response…",                type:"request", time:"T1", note:"Another thread held — OCS queue growing"},
          {id:5, from:"bng",   to:"aaa",   msg:"50,000 packets/sec\nAll routes → sync OCS",                   type:"request", time:"T2", note:"Every single subscriber hits synchronous OCS path"},
          {id:6, from:"ocs",   to:"ocs",   msg:"OCS queue depth: 50,000 ❌\nResponse time: 500ms+",           type:"danger",  time:"T2", note:"OCS overwhelmed — designed for 5,000 sync calls, receiving 50,000"},
          {id:7, from:"aaa",   to:"aaa",   msg:"Thread pool exhausted ❌\nAll threads waiting on OCS",        type:"crash",   time:"T3", note:"AAA threads parked on OCS I/O — no capacity for new RADIUS packets"},
          {id:8, from:"ocs",   to:"ocs",   msg:"OCS timeout → cascading failure ❌",                          type:"crash",   time:"T3", note:"OCS starts rejecting — billing stops, subscribers unmetered"},
          {id:9, from:"bng",   to:"bng",   msg:"RADIUS ACK timeout → retransmit storm",                      type:"retry",   time:"T3", note:"BNG retransmits compound the problem — positive feedback loop"},
          {id:10,from:"aaa",   to:"aaa",   msg:"System collapse ❌\n50M subscribers unmetered",               type:"crash",   time:"T4", note:"No billing, no enforcement, no quota management — full outage"},
        ]
      },
      by_value:{
        label:"✅ By Value Routing", color:"#22c55e",
        desc:"isHighRisk() → prepaid sync OCS; postpaid → async Kafka; 10× throughput",
        steps:[
          {id:1, from:"bng",   to:"aaa",   msg:"RADIUS Interim-Update\n(Postpaid subscriber)",                type:"request", time:"T1", note:"90% of traffic — postpaid user, low risk"},
          {id:2, from:"aaa",   to:"mongo", msg:"READ profile → planType=POSTPAID",                             type:"request", time:"T1"},
          {id:3, from:"aaa",   to:"aaa",   msg:"isHighRisk() = false ✅\n→ Async path",                       type:"success", time:"T1", note:"Postpaid = low risk — eventual consistency perfectly acceptable"},
          {id:4, from:"aaa",   to:"kafka", msg:"Publish delta to Kafka\nFire-and-forget ✅",                   type:"success", time:"T1", note:"AAA does NOT wait — thread free in <1ms, next packet immediately"},
          {id:5, from:"kafka", to:"cdr",   msg:"CDR processes at leisure\nEventual consistency",               type:"response",time:"T1", note:"Offline charging — no real-time constraint for postpaid"},
          {id:6, from:"bng",   to:"aaa",   msg:"RADIUS Interim-Update\n(Prepaid, balance < 10GB)",            type:"request", time:"T2", note:"10% of traffic — prepaid user with low balance, HIGH risk"},
          {id:7, from:"aaa",   to:"mongo", msg:"READ profile → PREPAID, balance=2GB",                          type:"request", time:"T2"},
          {id:8, from:"aaa",   to:"aaa",   msg:"isHighRisk() = true ✅\n→ Sync path",                         type:"self",    time:"T2", note:"Prepaid + low balance = zero revenue leakage tolerance"},
          {id:9, from:"aaa",   to:"ocs",   msg:"Sync RADIUS proxy → OCS\ndelta: +500MB",                      type:"request", time:"T2", note:"Only 10% hits sync OCS — 5,000/sec not 50,000/sec"},
          {id:10,from:"ocs",   to:"aaa",   msg:"OCS response: balance=1.5GB\nNo threshold crossed",           type:"response",time:"T2", note:"OCS handles 5K/sec easily — well within capacity"},
          {id:11,from:"aaa",   to:"bng",   msg:"RADIUS ACK ✅",                                                type:"success", time:"T3"},
          {id:12,from:"aaa",   to:"aaa",   msg:"Throughput: 50K pkt/sec ✅\nOCS load: 5K/sec (10%)",           type:"success", time:"T3", note:"10× throughput improvement — OCS never overwhelmed"},
        ]
      },
    }
  },

  // ─── C10: OCS Response Handling ────────────────────────────────────────
  {
    id:"c10", title:"OCS Response Handling",
    subtitle:"Four OCS Outcomes — From Silent ACK to CoA Type 2 Enforcement",
    color:"#0ea5e9", icon:"📡",
    actors:{
      aaa:   { label:"AAA Server",          layer:"nss" },
      ocs:   { label:"OCS",                 layer:"bss" },
      mongo: { label:"MongoDB",             layer:"db"  },
      bng:   { label:"BNG Router",          layer:"nss" },
      kafka: { label:"Kafka",               layer:"db"  },
      sms:   { label:"Notification / SMS",  layer:"bss" },
    },
    scenarios:{
      all_outcomes:{
        label:"✅ Four Response Paths", color:"#22c55e",
        desc:"Async skip → Normal ACK → Threshold SMS (no CoA) → Quota Exhausted (CoA Type 2 + Pessimistic View)",
        steps:[
          {id:1, from:"aaa",   to:"ocs",   msg:"RADIUS Accounting proxy\n(prepaid sync path)",                type:"request", time:"T1", note:"AAA proxies RADIUS accounting to OCS with usage delta"},
          {id:2, from:"ocs",   to:"aaa",   msg:"OUTCOME 1: Normal ACK\nNo special indicators",               type:"response",time:"T1", note:"OCS says: balance OK, no threshold crossed — AAA forwards ACK to BNG, done"},
          {id:3, from:"aaa",   to:"bng",   msg:"RADIUS ACK → BNG\nWait for next interim",                    type:"success", time:"T1", note:"Nothing to do — session continues normally"},
          {id:4, from:"ocs",   to:"aaa",   msg:"OUTCOME 2: Threshold 90%\nVSA: threshold_crossed=90",        type:"response",time:"T2", note:"OCS signals 90% quota used — AAA must notify, but NO speed change"},
          {id:5, from:"aaa",   to:"mongo", msg:"$addToSet: alertedThresholds: '90%'\n(idempotent check)",     type:"atomic",  time:"T2", note:"Idempotent: if already alerted for 90%, modifiedCount=0 → skip SMS"},
          {id:6, from:"aaa",   to:"kafka", msg:"ThresholdCrossedEvent(90%)\n→ Notification Service",          type:"success", time:"T2", note:"SMS: 'You have used 90% of your data. 10% remaining.'"},
          {id:7, from:"aaa",   to:"aaa",   msg:"NO CoA sent ✅\nSpeed unchanged at 90%",                     type:"success", time:"T2", note:"Critical: threshold notification ≠ enforcement. No speed change at 50%/90%."},
          {id:8, from:"ocs",   to:"aaa",   msg:"OUTCOME 3: Quota Exhausted ❗\nVSA: quota_exhausted=true",   type:"danger",  time:"T3", note:"100% quota used — OCS instructs AAA to enforce FUP throttle"},
          {id:9, from:"aaa",   to:"mongo", msg:"state = QUOTA_EXHAUSTED",                                     type:"request", time:"T3", note:"AAA saves exhaustion state before enforcement"},
          {id:10,from:"aaa",   to:"bng",   msg:"CoA Type 2: speed = 1Mbps\n(FUP enforcement)",                type:"request", time:"T3", note:"AAA sends enforcement CoA to BNG — throttle subscriber to FUP speed"},
          {id:11,from:"bng",   to:"aaa",   msg:"CoA-ACK ✅ Throttle applied",                                type:"success", time:"T4", note:"BNG confirms hardware now at 1Mbps — enforcement complete"},
          {id:12,from:"aaa",   to:"kafka", msg:"[PESSIMISTIC VIEW]\nThresholdCrossedEvent(100%)",             type:"success", time:"T4", note:"ONLY after CoA-ACK: publish event for SMS. Never before hardware confirms."},
          {id:13,from:"sms",   to:"sms",   msg:"SMS: 'Data exhausted.\nSpeed reduced to 1Mbps.'",            type:"success", time:"T4", note:"Subscriber notified after hardware confirmed — zero false promises"},
        ]
      },
      quota_exhausted:{
        label:"❌ Notify Before CoA-ACK", color:"#ef4444",
        desc:"SMS sent before BNG confirms throttle → subscriber sees 'throttled' but still at full speed",
        steps:[
          {id:1, from:"ocs",   to:"aaa",   msg:"Quota Exhausted signal\nVSA: quota_exhausted=true",          type:"danger",  time:"T1", note:"OCS instructs AAA to enforce FUP throttle"},
          {id:2, from:"aaa",   to:"mongo", msg:"state = QUOTA_EXHAUSTED",                                     type:"request", time:"T1"},
          {id:3, from:"aaa",   to:"kafka", msg:"ThresholdCrossedEvent(100%) ❌\nSent BEFORE CoA!",            type:"danger",  time:"T1", note:"WRONG: notifying before hardware confirms — violates Pessimistic View"},
          {id:4, from:"sms",   to:"sms",   msg:"SMS: 'Speed reduced to 1Mbps' ❌",                           type:"danger",  time:"T1", note:"Subscriber receives SMS — but BNG hasn't throttled yet!"},
          {id:5, from:"aaa",   to:"bng",   msg:"CoA Type 2: speed = 1Mbps",                                   type:"request", time:"T2", note:"CoA sent after notification — wrong order"},
          {id:6, from:"bng",   to:"bng",   msg:"BNG reboot / CoA lost 💥",                                   type:"crash",   time:"T2", note:"BNG reboots — CoA never applied. Subscriber still at full speed."},
          {id:7, from:"bng",   to:"bng",   msg:"Subscriber still at 300Mbps ❌\nSMS said 1Mbps",             type:"danger",  time:"T3", note:"SMS says throttled but subscriber enjoying full speed — free data usage"},
          {id:8, from:"aaa",   to:"aaa",   msg:"No retry — state says EXHAUSTED\nbut BNG never throttled ❌",type:"danger",  time:"T3", note:"AAA thinks enforcement is done. Network and DB disagree. Revenue leakage."},
          {id:9, from:"ocs",   to:"ocs",   msg:"Subscriber uses unlimited data ❌\nBilling: 'throttled'",    type:"danger",  time:"T4", note:"Revenue loss + trust damage + regulatory risk — all from wrong notification order"},
        ]
      },
    }
  },
];

// ─────────────────────────────────────────────
// SEQUENCE DIAGRAM RENDERER
// ─────────────────────────────────────────────
function SequenceDiagram({ concept, scenarioKey }) {
  const [hovered, setHovered] = useState(null);
  const scenario = concept.scenarios[scenarioKey];
  const actorKeys = Object.keys(concept.actors);
  const COL_W = 118, ROW_H = 60, HEADER_H = 82, LEFT_PAD = 20, LABEL_W = 52;
  const W = LEFT_PAD + LABEL_W + actorKeys.length * COL_W + 50;
  const H = HEADER_H + scenario.steps.length * ROW_H + 40;
  const getX = k => LEFT_PAD + LABEL_W + actorKeys.indexOf(k) * COL_W + COL_W / 2;

  return (
    <div style={{ overflowX: "auto", overflowY: "visible" }}>
      <svg width={W} height={H} style={{ display: "block", margin: "0 auto", fontFamily: "'JetBrains Mono', monospace" }}>
        <defs>
          {Object.keys(MS).map(t => (
            <marker key={t} id={`a${t}`} markerWidth={7} markerHeight={7} refX={5} refY={3} orient="auto">
              <path d="M0,0 L0,6 L7,3 z" fill={MS[t] || "#60a5fa"} />
            </marker>
          ))}
        </defs>
        {/* Actors */}
        {actorKeys.map(key => {
          const a = concept.actors[key];
          const lc = LC[a.layer] || LC.nss;
          const cx = getX(key);
          return (
            <g key={key}>
              <line x1={cx} y1={HEADER_H} x2={cx} y2={H - 20}
                stroke={lc.border} strokeWidth={1} strokeDasharray="3,5" opacity={0.2} />
              <rect x={cx - 50} y={6} width={100} height={68} rx={6}
                fill={lc.bg} stroke={lc.border} strokeWidth={1.5} />
              {a.label.split(" ").map((w, i) => (
                <text key={i} x={cx} y={28 + i * 14} textAnchor="middle"
                  fill={lc.text} fontSize={9} fontWeight={700} letterSpacing={0.5}>{w}</text>
              ))}
            </g>
          );
        })}
        {/* Steps */}
        {scenario.steps.map((step, i) => {
          const y = HEADER_H + i * ROW_H + ROW_H / 2;
          const fx = getX(step.from), tx = getX(step.to);
          const isSelf = step.from === step.to;
          const isRight = tx > fx;
          const color = MS[step.type] || "#60a5fa";
          const isDash = DASH[step.type] || false;
          const isHov = hovered === step.id;
          const tc = TC[step.time] || "#475569";
          const lines = step.msg.split("\n");
          return (
            <g key={step.id}
              onMouseEnter={() => setHovered(step.id)}
              onMouseLeave={() => setHovered(null)}
              style={{ cursor: "default" }}>
              {isHov && (
                <rect x={LEFT_PAD + LABEL_W} y={y - ROW_H / 2 + 2}
                  width={W - LEFT_PAD - LABEL_W - 40} height={ROW_H - 4}
                  fill={color} opacity={0.06} rx={3} />
              )}
              {/* Time badge */}
              <rect x={LEFT_PAD} y={y - 11} width={LABEL_W - 4} height={22} rx={4}
                fill={tc + "20"} stroke={tc} strokeWidth={1} />
              <text x={LEFT_PAD + (LABEL_W - 4) / 2} y={y + 4}
                textAnchor="middle" fill={tc} fontSize={9} fontWeight={700}>{step.time}</text>
              {/* Step number */}
              <circle cx={LEFT_PAD + LABEL_W + 11} cy={y} r={9} fill={color} opacity={0.18} />
              <text x={LEFT_PAD + LABEL_W + 11} y={y + 3.5}
                textAnchor="middle" fill={color} fontSize={8} fontWeight={700}>{step.id}</text>
              {/* Arrow */}
              {isSelf ? (
                <>
                  <path d={`M${fx} ${y-14} C${fx+52} ${y-14} ${fx+52} ${y+14} ${fx} ${y+14}`}
                    fill="none" stroke={color} strokeWidth={isHov ? 2 : 1.5}
                    strokeDasharray={isDash ? "5,3" : "none"} />
                  <polygon points={`${fx},${y+14} ${fx-6},${y+8} ${fx-6},${y+20}`} fill={color} />
                  {lines.map((ln, li) => (
                    <text key={li} x={fx + 57} y={y - 8 + li * 12}
                      fill={color} fontSize={8.5} fontWeight={600}>{ln}</text>
                  ))}
                </>
              ) : (
                <>
                  <line x1={fx} y1={y} x2={isRight ? tx - 5 : tx + 5} y2={y}
                    stroke={color} strokeWidth={isHov ? 2.5 : 1.5}
                    strokeDasharray={isDash ? "6,3" : "none"}
                    markerEnd={`url(#a${step.type})`} />
                  {lines.map((ln, li) => {
                    const mid = (fx + tx) / 2;
                    const off = (lines.length - 1) * 5;
                    return (
                      <text key={li} x={mid} y={y - 5 + li * 11 - off}
                        textAnchor="middle" fill={color} fontSize={8.5} fontWeight={600}>{ln}</text>
                    );
                  })}
                </>
              )}
              {/* Hover note */}
              {isHov && step.note && (
                <g>
                  <rect x={W - 235} y={y - 14} width={228} height={26} rx={4}
                    fill="#0a0f1e" stroke={color} strokeWidth={1} />
                  <text x={W - 121} y={y + 3} textAnchor="middle"
                    fill={color} fontSize={8.5} fontWeight={600}>💡 {step.note}</text>
                </g>
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

// ─────────────────────────────────────────────
// CONCEPT PROBLEM BAR
// ─────────────────────────────────────────────
function ConceptProblem({ id, color }) {
  const problems = {
    c1: <> User pays for a booster at 11:59 PM — CRM collects payment. Concurrently, the midnight suspension job runs for an unpaid main plan bill. Without a semantic lock, both operations read <span style={{color:"#f59e0b"}}>ACTIVE</span>. Suspension writes <span style={{color:"#ef4444"}}>SUSPENDED</span>; booster completes and overwrites it back to <span style={{color:"#ef4444"}}>ACTIVE</span> — the suspended account gets 150Mbps, no refund is triggered, and the conflict is completely invisible to the BSS layer. Both operator and subscriber lose. </>,
    c2: <> Three pod crash scenarios arise. <span style={{color:"#22c55e"}}>Scenario A</span> (crash before CoA) and <span style={{color:"#f59e0b"}}>Scenario B</span> (crash while waiting for CoA-ACK) are watchdog-recoverable. <span style={{color:"#ef4444"}}>Scenario C</span> is the most dangerous — pod crashes after CoA-ACK but before MongoDB commit. BNG hardware is already upgraded to 150Mbps, but the watchdog blindly marks state = <span style={{color:"#ef4444"}}>FAILED</span> and triggers a refund for a plan actively running on the network. The Outbox eliminates all three by writing the CoA payload atomically with the state change — CoA delivery is retried independently of the pod lifecycle. Applied selectively to Flow 1 only; Flow 2 requires sub-500ms RADIUS response where relay lag is unacceptable. </>,
    c3: <> AAA Server runs Active-Active across Mumbai and Gujarat data centres with 20–40ms MongoDB replication lag. Gujarat AAA Server sends CoA → BNG to upgrade to 150Mbps, holding <span style={{color:"#f59e0b"}}>@Version=5</span>. During the CoA window, an admin in Mumbai emergency-downgrades the plan — Mumbai saves <span style={{color:"#06b6d4"}}>@Version=6, speed=100Mbps</span>. Gujarat receives CoA-ACK (hardware already at 150Mbps) and blindly saves <span style={{color:"#ef4444"}}>@Version=5, speed=150Mbps</span> — silently overwriting the admin's change. With <span style={{color:"#22c55e"}}>@Version</span>, the conflict is detected. AAA Server re-reads fresh state and applies a <span style={{color:"#f59e0b"}}>state-first check</span> — three cases: if fresh state is <span style={{color:"#ef4444"}}>QUOTA_EXHAUSTED</span>, OCS is the authority and no corrective CoA is sent; if state is <span style={{color:"#22c55e"}}>ACTIVE</span> and speed differs, admin changed the plan — send <span style={{color:"#f43f5e"}}>corrective CoA</span>; if state is <span style={{color:"#22c55e"}}>ACTIVE</span> and speed matches, just re-save. <span style={{color:"#06b6d4"}}>OCS is the quota authority; AAA Server never overrides an OCS throttle via corrective CoA unless MongoDB state explicitly confirms quota has been reset.</span> </>,
    c4: <> Optimistic notification — notifying CRM before BNG confirms — is the <span style={{color:"#ef4444"}}>number one cause of subscriber complaints</span> in broadband. AAA Server tells CRM "Success!" immediately; user's app shows 150Mbps. Then BNG reboots, the CoA is lost, and the user is still on 50Mbps. Pessimistic View holds back all downstream notifications until <span style={{color:"#22c55e"}}>CoA-ACK is received</span>. The same rule applies in Flow 2 — the FUP throttle SMS is sent only after BNG confirms the 1Mbps policy is applied on hardware. The cost: a few seconds of "Processing…". The gain: zero false promises. </>,
    c6: <> The AAA Server handles <span style={{color:"#f43f5e"}}>50,000 RADIUS packets/sec</span> on Spring WebFlux backed by Netty's event loop — typically <span style={{color:"#f43f5e"}}>16 threads</span> for 50K+ req/sec. A single blocking <code style={{color:"#f87171",fontSize:9}}>mongoTemplate.findOne()</code> inside the handler parks the Netty thread on a kernel I/O wait. When sixteen packets arrive simultaneously all sixteen threads park. The event loop is <span style={{color:"#ef4444"}}>completely starved</span>: no thread is free to accept new packets. UDP socket buffers fill, packets are dropped, RFC 2865 clients retransmit, the retransmit storm compounds — <span style={{color:"#ef4444"}}>full throughput collapse</span>. The fix is a single import swap: <code style={{color:"#22c55e",fontSize:9}}>ReactiveMongoTemplate</code> returns <code style={{color:"#22c55e",fontSize:9}}>Mono&lt;&gt;</code> in under a millisecond. The Netty thread is free instantly. MongoDB I/O completes asynchronously; the handler resumes on a callback. 50,000 packets/sec — sustained with sixteen threads. </>,
    c7: <> Redis holds live session quota state: <span style={{color:"#f59e0b"}}>quota_used=8GB</span> for every active subscriber. During a Redis cluster rolling upgrade or node failure, a <span style={{color:"#ef4444"}}>5–30 second outage window</span> occurs. The next RADIUS Interim-Update from BNG arrives — AAA performs a cache lookup, receives a connection timeout, and defaults <span style={{color:"#ef4444"}}>quota_used=0</span>. The subscriber appears to have a completely fresh quota. BNG is told to continue the session. The subscriber consumes an additional 10GB without enforcement. The CDR closes with 10GB — the operator has delivered 18GB and billed for 10GB: <span style={{color:"#ef4444"}}>silent revenue leakage</span>. Write-through caching ensures every quota update is written to <span style={{color:"#22c55e"}}>Redis and MongoDB simultaneously</span>. On a Redis cache miss, AAA falls back to MongoDB — which always holds accurate state from the write-through writes. Billing error window: <span style={{color:"#22c55e"}}>zero</span>. </>,
    c8: <> RADIUS <code style={{color:"#e879f9",fontSize:9}}>Acct-Output-Octets</code> is a <span style={{color:"#e879f9"}}>32-bit counter</span> — max 4,294,967,295 bytes (4GB). At Service Provider Fiber speeds this wraps constantly: <span style={{color:"#ef4444"}}>every 1.8 minutes at 300Mbps</span>, every 32 seconds at 1Gbps. Without RFC 2869 <span style={{color:"#e879f9"}}>Gigawords</span> handling, the counter resets near zero after wrap — AAA Server calculates a <span style={{color:"#ef4444"}}>negative delta</span>, clamps to zero, and OCS receives wrong usage. Subscriber uses data for free. With Gigawords: <code style={{color:"#22c55e",fontSize:9}}>trueTotal = (Gigawords × 4,294,967,296) + Octets</code>. Delta is always correct. And because AAA sends only <span style={{color:"#22c55e"}}>relative deltas</span> (not absolute totals), the OCS operation <code style={{color:"#22c55e",fontSize:9}}>balance = balance - delta</code> is <span style={{color:"#22c55e"}}>commutative</span> — order-independent, no row locks, thousands processed concurrently. </>,
    c9: <> Without By Value routing, <span style={{color:"#ef4444"}}>all 50 million subscribers</span> hit synchronous OCS on every RADIUS Interim-Update — the OCS becomes the bottleneck and <span style={{color:"#ef4444"}}>the system collapses</span>. But 90% of subscribers are postpaid where eventual consistency is perfectly acceptable — there is no real-time enforcement need. Only <span style={{color:"#8b5cf6"}}>10% are prepaid with low balance</span> where zero revenue leakage tolerance demands synchronous OCS calls. <code style={{color:"#8b5cf6",fontSize:9}}>isHighRisk()</code> checks <span style={{color:"#8b5cf6"}}>PREPAID + balance &lt; 10GB</span>: high-risk gets sync RADIUS proxy to OCS; low-risk gets async Kafka to CDR system. Result: OCS load drops from 50K/sec to 5K/sec — <span style={{color:"#22c55e"}}>10× throughput improvement</span>. A deliberate business decision to accept eventual consistency for the majority. </>,
    c10: <> OCS returns four possible responses after evaluating quota. <span style={{color:"#22c55e"}}>Normal ACK</span> — balance OK, wait for next interim. <span style={{color:"#f59e0b"}}>Threshold crossed</span> (50%/90%) — send idempotent SMS notification but <span style={{color:"#f59e0b"}}>NO speed change, NO CoA</span>. <span style={{color:"#ef4444"}}>Quota exhausted</span> (100%) — the critical path: AAA must send <span style={{color:"#ef4444"}}>CoA Type 2 enforcement</span> to BNG (throttle to 1Mbps), wait for <span style={{color:"#22c55e"}}>CoA-ACK</span>, and only THEN publish the notification event for SMS. Confusing threshold (notify-only) with exhaustion (enforce) causes either <span style={{color:"#ef4444"}}>false throttle at 50%</span> or <span style={{color:"#ef4444"}}>missed enforcement at 100%</span>. OCS is the <span style={{color:"#0ea5e9"}}>decision maker</span>; AAA Server is the <span style={{color:"#0ea5e9"}}>enforcer</span>; BNG owns the <span style={{color:"#0ea5e9"}}>hardware</span>. AAA never independently decides to throttle. </>,
  };
  return (
    <div style={{ background:"#080e1c", border:`1px solid ${color}`, borderRadius:8, padding:"10px 16px", marginBottom:14, fontSize:9.5, color:"#94a3b8", lineHeight:1.85 }}>
      <span style={{ color, fontWeight:700 }}>The Problem: </span>
      {problems[id]}
    </div>
  );
}

// ─────────────────────────────────────────────
// MAIN APP
// ─────────────────────────────────────────────
export default function App({ initialConceptId = "c1", onBack } = {}) {
  const [activeConceptId, setActiveConceptId] = useState(initialConceptId);
  const [activeScenarios, setActiveScenarios] = useState({
    c1:"without", c2:"crash_c", c3:"without_version", c4:"optimistic_wrong",
    c6:"blocking", c7:"redis_fail", c8:"without_gigawords", c9:"all_sync", c10:"quota_exhausted",
  });

  const concept = CONCEPTS.find(c => c.id === activeConceptId);
  const scenarioKey = activeScenarios[activeConceptId];
  const scenario = concept.scenarios[scenarioKey];

  const setScenario = (k) => setActiveScenarios(p => ({ ...p, [activeConceptId]: k }));

  return (
    <div style={{
      background: "#050a14", minHeight: "100vh",
      fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
      color: "#e2e8f0",
    }}>


      <div style={{ padding: "16px 20px" }}>

        {/* ── SCENARIO BUTTONS ── */}
        <div style={{ display: "flex", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
          {Object.entries(concept.scenarios).map(([k, sc]) => (
            <button key={k} onClick={() => setScenario(k)} style={{
              padding: "8px 18px",
              background: scenarioKey === k ? sc.color : "#0d1625",
              color: scenarioKey === k ? "#000" : "#64748b",
              border: `1px solid ${scenarioKey === k ? sc.color : "#1a2740"}`,
              borderRadius: 6, cursor: "pointer", fontSize: 11,
              fontWeight: 700, fontFamily: "inherit", letterSpacing: 0.3,
              transition: "all 0.15s",
            }}>{sc.label}</button>
          ))}
          <div style={{ marginLeft: "auto", fontSize: 9, color: "#1e3a5f", alignSelf: "center" }}>
            Hover any step to see notes
          </div>
        </div>

        {/* ── DIAGRAM ── */}
        <div style={{
          background: "#080e1c", border: "1px solid #0f1e38",
          borderRadius: 10, padding: "16px 8px",
          marginBottom: 14,
        }}>
          <SequenceDiagram concept={concept} scenarioKey={scenarioKey} />
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────
// PLAIN TEXT EXPORTS (for editable fields)
// ─────────────────────────────────────────────
export const PROBLEM_TEXTS = {
  c1:  "User pays for a booster at 11:59 PM — CRM collects payment. Concurrently, the midnight suspension job runs for an unpaid main plan bill. Without a semantic lock, both operations read ACTIVE. Suspension writes SUSPENDED; booster completes and overwrites it back to ACTIVE — the suspended account gets 150Mbps, no refund is triggered, and the conflict is completely invisible to the BSS layer. Both operator and subscriber lose.",
  c2:  "Three pod crash scenarios arise. Scenario A (crash before CoA) and Scenario B (crash while waiting for CoA-ACK) are watchdog-recoverable. Scenario C is the most dangerous — pod crashes after CoA-ACK but before MongoDB commit. BNG hardware is already upgraded to 150Mbps, but the watchdog blindly marks state = FAILED and triggers a refund for a plan actively running on the network. The Outbox eliminates all three by writing the CoA payload atomically with the state change — CoA delivery is retried independently of the pod lifecycle. Applied selectively to Flow 1 only; Flow 2 requires sub-500ms RADIUS response where relay lag is unacceptable.",
  c3:  "AAA Server runs Active-Active across Mumbai and Gujarat data centres with 20–40ms MongoDB replication lag. Gujarat AAA Server sends CoA → BNG to upgrade to 150Mbps, holding @Version=5. During the CoA window, an admin in Mumbai emergency-downgrades the plan — Mumbai saves @Version=6, speed=100Mbps. Gujarat receives CoA-ACK (hardware already at 150Mbps) and blindly saves @Version=5, speed=150Mbps — silently overwriting the admin's change. With @Version, the conflict is detected. AAA Server re-reads fresh state: if QUOTA_EXHAUSTED → OCS is authority, no corrective CoA; if ACTIVE and speed differs → send corrective CoA; if ACTIVE and speed matches → re-save. OCS is the quota authority; AAA Server never overrides an OCS throttle unless MongoDB state explicitly confirms quota has been reset.",
  c4:  "Optimistic notification — notifying CRM before BNG confirms — is the number one cause of subscriber complaints in broadband. AAA Server tells CRM 'Success!' immediately; user's app shows 150Mbps. Then BNG reboots, the CoA is lost, and the user is still on 50Mbps. Pessimistic View holds back all downstream notifications until CoA-ACK is received. The same rule applies in Flow 2 — the FUP throttle SMS is sent only after BNG confirms the 1Mbps policy is applied on hardware. The cost: a few seconds of 'Processing…'. The gain: zero false promises.",
  c6:  "The AAA Server handles 50,000 RADIUS packets/sec on Spring WebFlux backed by Netty's event loop — typically 16 threads for 50K+ req/sec. A single blocking mongoTemplate.findOne() inside the handler parks the Netty thread on a kernel I/O wait. When sixteen packets arrive simultaneously all sixteen threads park. The event loop is completely starved: no thread is free to accept new packets. UDP socket buffers fill, packets are dropped, RFC 2865 clients retransmit, the retransmit storm compounds — full throughput collapse. The fix: a single import swap to ReactiveMongoTemplate returns Mono<> in under a millisecond. The Netty thread is free instantly. 50,000 packets/sec — sustained with sixteen threads.",
  c7:  "Redis holds live session quota state: quota_used=8GB for every active subscriber. During a Redis cluster rolling upgrade or node failure, a 5–30 second outage window occurs. The next RADIUS Interim-Update arrives — AAA performs a cache lookup, receives a connection timeout, and defaults quota_used=0. The subscriber appears to have a completely fresh quota. BNG is told to continue the session. The subscriber consumes an additional 10GB without enforcement. The CDR closes with 10GB — the operator has delivered 18GB and billed for 10GB: silent revenue leakage. Write-through caching ensures every quota update is written to Redis and MongoDB simultaneously. On a Redis cache miss, AAA falls back to MongoDB. Billing error window: zero.",
  c8:  "RADIUS Acct-Output-Octets is a 32-bit counter — max 4,294,967,295 bytes (4GB). At Service Provider Fiber speeds this wraps constantly: every 1.8 minutes at 300Mbps, every 32 seconds at 1Gbps. Without RFC 2869 Gigawords handling, the counter resets near zero after wrap — AAA Server calculates a negative delta, clamps to zero, and OCS receives wrong usage. Subscriber uses data for free. With Gigawords: trueTotal = (Gigawords × 4,294,967,296) + Octets. Delta is always correct. Because AAA sends only relative deltas, the OCS operation balance = balance - delta is commutative — order-independent, no row locks, thousands processed concurrently.",
  c9:  "Without By Value routing, all 50 million subscribers hit synchronous OCS on every RADIUS Interim-Update — the OCS becomes the bottleneck and the system collapses. But 90% of subscribers are postpaid where eventual consistency is perfectly acceptable. Only 10% are prepaid with low balance where zero revenue leakage tolerance demands synchronous OCS calls. isHighRisk() checks PREPAID + balance < 10GB: high-risk gets sync RADIUS proxy to OCS; low-risk gets async Kafka to CDR system. Result: OCS load drops from 50K/sec to 5K/sec — 10× throughput improvement.",
  c10: "OCS returns four possible responses after evaluating quota. Normal ACK — balance OK, wait for next interim. Threshold crossed (50%/90%) — send idempotent SMS notification but NO speed change, NO CoA. Quota exhausted (100%) — the critical path: AAA must send CoA Type 2 enforcement to BNG (throttle to 1Mbps), wait for CoA-ACK, and only THEN publish the notification event for SMS. Confusing threshold (notify-only) with exhaustion (enforce) causes either false throttle at 50% or missed enforcement at 100%. OCS is the decision maker; AAA Server is the enforcer; BNG owns the hardware.",
};

export const INTERVIEW_TEXTS = {
  c1:  `"The Semantic Lock prevents Lost Updates — but the deeper insight is the BUSINESS impact. Without it, PCF overwrites suspension, the user enjoys 150Mbps on an unpaid account, and critically — no compensating transaction is ever triggered.\n\nThe lock handles both race orderings: booster first → suspension 409s and retries. Suspension first → pre-check reads SUSPENDED and fails fast without sending any CoA to BNG — no hardware confusion.\n\nNote: when suspension wins, it must also send a CoA to BNG — a database state change alone does not change hardware. We wait for that CoA-ACK before publishing the conflict event. Pessimistic View applied here too.\n\nBetween the booster CoA-ACK and the suspension CoA-ACK there is a brief window where the subscriber briefly gets 150Mbps on a suspended account. This is acceptable — the suspension CoA is sent immediately after the pivot decision, the window is milliseconds, and the alternative without a lock is permanent corruption.\n\nThis maps to Chapter 4: booster = Compensatable, suspension = Pivot, refund = Compensating Transaction."`,
  c2:  `"We apply Outbox selectively based on latency profile. Flow 2 needs sub-500ms RADIUS response — polling lag makes Outbox unsuitable. Flow 1 is a business event — user waits on Processing screen — Outbox is appropriate.\n\nThe critical insight: TTL watchdog alone cannot distinguish three crash scenarios. Scenario C is most dangerous — BNG hardware is upgraded but we mark FAILED and refund an active plan. Outbox solves all three by making CoA retryable from persistent MongoDB state.\n\nOne additional coordination: when watchdog fires, it also marks related outbox rows ABANDONED — preventing a resuming relay from sending stale CoA commands to BNG.\n\nFor the residual edge case — Scenario C plus relay failure — network divergence persists until the reconciliation job or the next RADIUS Interim-Update detects the mismatch. In telecom, 5-minute reconciliation is the standard window. But billing discrepancies always require human intervention — the automated job corrects the network, but only Revenue Assurance can determine the correct financial outcome for the subscriber."`,
  c3:  `"The CoA-ACK is our pivot point — once BNG confirms, hardware is already at the new speed. When OptimisticLockingException occurs, we cannot simply re-read and save — the router is running at a speed that may no longer match the database.\n\nWe check if fresh MongoDB state differs from what we told BNG. If admin changed the plan during our CoA window, we send a corrective CoA to realign hardware with admin's intent. Network state must always equal database state — that is a fundamental carrier-grade requirement.\n\nFor audit and safety: corrective CoA is fully logged, ops team alerted, and rate-limited. Human oversight is monitoring and alerting — not manual approval per subscriber, which would be impossible at carrier scale."`,
  c4:  `"Pessimistic View means we hide unconfirmed state from all downstream systems. Nobody sees success until hardware confirms it. We apply this in both provisioning and FUP enforcement — the subscriber gets the SMS about speed reduction only after BNG confirms the throttle via CoA-ACK.\n\nFor FUP specifically: OCS is the decision maker — AAA Server is the enforcer. AAA Server proxies RADIUS accounting to OCS. OCS signals quota exhaustion via RADIUS accounting-response VSA attribute. AAA Server reads that instruction and sends enforcement CoA Type 2 to BNG. AAA Server never independently decides to throttle — it always waits for OCS instruction.\n\nThe business impact is clear: optimistic notification is the number one cause of subscriber complaints in broadband. Pessimistic View eliminates that entirely at the cost of a few seconds of 'Processing...' on the screen."`,
  c6:  `"The Netty event loop is the backbone of WebFlux throughput. Sixteen threads handle 50,000 RADIUS packets per second — but only because each handler returns in microseconds. The moment you introduce a blocking call, that thread parks on kernel I/O. Sixteen packets arrive simultaneously: sixteen threads park, zero capacity remains. Every subsequent packet queues in the UDP socket buffer until the kernel drops it. RFC 2865 clients retransmit dropped packets. The retransmit storm makes the situation worse — a positive feedback loop toward complete collapse.\n\nThe fix is not architectural. It is a single import swap from MongoTemplate to ReactiveMongoTemplate. The reactive template returns a Mono<> in under a millisecond. The Netty thread is released immediately to handle the next packet.\n\nIn production we add two guardrails: @Blocking on any method that must never be called reactively — Spring throws at startup if violated — and BlockHound in the test suite, which fails immediately if any test path parks a thread."`,
  c7:  `"Redis gives us sub-millisecond session reads — critical when processing 50,000 RADIUS packets per second. But Redis is volatile by design: a rolling cluster upgrade, a network partition, or memory pressure eviction can drop session state in seconds. This is an operational reality in every carrier deployment.\n\nWithout a fallback, the AAA Server defaults quota_used to zero on a cache miss. The subscriber's quota appears completely fresh. BNG receives an ACK, the session continues unrestricted, and the subscriber accumulates gigabytes beyond their plan limit. The CDR closes with the ghost session data — the operator delivers 18GB and bills for 10GB. Silent revenue leakage at carrier scale.\n\nWrite-through caching treats MongoDB as the system of record for quota state. Every interim-update write goes to Redis and MongoDB together. When Redis fails, the fallback reads MongoDB and gets the accurate current value immediately. The billing error window is zero — not 5 seconds, not 30 seconds: zero."`,
  c8:  `"Per RFC 2866, BNG always sends cumulative octets since session start. At 300Mbps Service Provider Fiber speed, the 32-bit counter wraps every 1.8 minutes — in a 20-minute interval it wraps 11 times. RFC 2869 Gigawords handling is mandatory, not optional.\n\nAAA Server calculates delta using the Gigawords-aware formula and proxies only the delta to OCS. Because we send relative deltas, not absolute totals, the OCS operation 'balance = balance - delta' is commutative — order doesn't matter, no row locks needed. OCS processes thousands of concurrent deductions without contention.\n\nThe previous cumulative total is stored in Redis for fast lookup, with write-through to MongoDB for failover safety. If Redis fails, the fallback reads MongoDB and the delta calculation remains correct."`,
  c9:  `"By Value is our most important performance decision. We actively ACCEPT eventual consistency for 90% of postpaid subscribers — that is a deliberate business choice, not a technical workaround. The 10% prepaid with low balances get synchronous OCS calls because we have zero tolerance for revenue leakage on prepaid.\n\nWithout this routing decision, we would need synchronous OCS calls for all 50 million subscribers. The OCS is designed for 5,000 synchronous calls per second — not 50,000. It would collapse immediately. By Value reduces OCS sync load by 90%, keeping it well within capacity while guaranteeing zero revenue leakage for the subscribers where it matters most.\n\nThe key insight: eventual consistency is perfectly acceptable for postpaid subscribers because they are billed monthly and any delta discrepancy reconciles at billing cycle close."`,
  c10: `"OCS response handling is where all the Flow 2 concepts converge. The OCS has four possible responses, and each demands a completely different action from AAA Server.\n\nNormal ACK: do nothing, forward to BNG, wait for next interim. Threshold crossed at 50% or 90%: send an idempotent SMS notification using $addToSet — but critically, NO CoA, NO speed change. Quota exhausted at 100%: this is the enforcement path — update state to QUOTA_EXHAUSTED, send CoA Type 2 to BNG with FUP speed of 1Mbps, wait for CoA-ACK confirming hardware throttled, and only then publish the notification event for SMS.\n\nThe most dangerous confusion is between threshold and exhaustion. If you send a CoA at 50%, you have throttled a subscriber who still has half their data. If you skip enforcement at 100%, the subscriber uses data for free.\n\nOCS is the decision maker for all quota matters. AAA Server never independently decides to throttle."`,
};

// ─────────────────────────────────────────────
// PER-CONCEPT INTERVIEW COMPONENTS
// ─────────────────────────────────────────────
function C2Interview() {
  return <Interview text={`"We apply Outbox selectively based on latency profile. Flow 2 needs sub-500ms RADIUS response — polling lag makes Outbox unsuitable. Flow 1 is a business event — user waits on Processing screen — Outbox is appropriate.

The critical insight: TTL watchdog alone cannot distinguish three crash scenarios. Scenario C is most dangerous — BNG hardware is upgraded but we mark FAILED and refund an active plan. Outbox solves all three by making CoA retryable from persistent MongoDB state.

One additional coordination: when watchdog fires, it also marks related outbox rows ABANDONED — preventing a resuming relay from sending stale CoA commands to BNG.

For the residual edge case — Scenario C plus relay failure — network divergence persists until the reconciliation job or the next RADIUS Interim-Update detects the mismatch. In telecom, 5-minute reconciliation is the standard window. But billing discrepancies always require human intervention — the automated job corrects the network, but only Revenue Assurance can determine the correct financial outcome for the subscriber. That is a deliberate boundary — billing decisions should never be fully automated at carrier scale."`} />;
}

function C3Interview() {
  return <Interview text={`"The CoA-ACK is our pivot point — once BNG confirms, hardware is already at the new speed. When OptimisticLockingException occurs, we cannot simply re-read and save — the router is running at a speed that may no longer match the database.

We check if fresh MongoDB state differs from what we told BNG. If admin changed the plan during our CoA window, we send a corrective CoA to realign hardware with admin's intent. Network state must always equal database state — that is a fundamental carrier-grade requirement.

For audit and safety: corrective CoA is fully logged, ops team alerted, and rate-limited. Human oversight is monitoring and alerting — not manual approval per subscriber, which would be impossible at carrier scale."`} />;
}

function C4Interview() {
  return <Interview text={`"Pessimistic View means we hide unconfirmed state from all downstream systems. Nobody sees success until hardware confirms it. We apply this in both provisioning and FUP enforcement — the subscriber gets the SMS about speed reduction only after BNG confirms the throttle via CoA-ACK.

For FUP specifically: OCS is the decision maker — AAA Server is the enforcer. AAA Server proxies RADIUS accounting to OCS. OCS signals quota exhaustion via RADIUS accounting-response VSA attribute. AAA Server reads that instruction and sends enforcement CoA Type 2 to BNG. AAA Server never independently decides to throttle — it always waits for OCS instruction. This separation is fundamental: OCS owns quota decisions, AAA Server owns network enforcement, BNG owns hardware. OCS never talks to BNG directly — AAA Server is the only bridge.

The business impact is clear: optimistic notification is the number one cause of subscriber complaints in broadband. User's app shows 150Mbps but they get 50Mbps — that is a support call. Pessimistic View eliminates that entirely at the cost of a few seconds of 'Processing...' on the screen."`} />;
}

function C6Interview() {
  return <Interview text={`"The Netty event loop is the backbone of WebFlux throughput. Sixteen threads handle 50,000 RADIUS packets per second — but only because each handler returns in microseconds. The moment you introduce a blocking call, that thread parks on kernel I/O. Sixteen packets arrive simultaneously: sixteen threads park, zero capacity remains. Every subsequent packet queues in the UDP socket buffer until the kernel drops it. RFC 2865 clients retransmit dropped packets. The retransmit storm makes the situation worse — a positive feedback loop toward complete collapse.

The fix is not architectural. It is a single import swap from MongoTemplate to ReactiveMongoTemplate. The reactive template returns a Mono<> in under a millisecond. The Netty thread is released immediately to handle the next packet. When MongoDB responds, the callback fires on whichever Netty thread is free at that moment. The event loop never accumulates debt.

In production we add two guardrails: @Blocking on any method that must never be called reactively — Spring throws at startup if violated — and BlockHound in the test suite, which fails immediately if any test path parks a thread. Blocking calls in a reactive WebFlux pipeline are the most common root cause of carrier-grade throughput collapse we encounter."`} />;
}

function C7Interview() {
  return <Interview text={`"Redis gives us sub-millisecond session reads — critical when processing 50,000 RADIUS packets per second. But Redis is volatile by design: a rolling cluster upgrade, a network partition, or memory pressure eviction can drop session state in seconds. This is an operational reality in every carrier deployment.

Without a fallback, the AAA Server defaults quota_used to zero on a cache miss. The subscriber's quota appears completely fresh. BNG receives an ACK, the session continues unrestricted, and the subscriber accumulates gigabytes beyond their plan limit. The CDR closes with the ghost session data — the operator delivers 18GB and bills for 10GB. Silent revenue leakage at carrier scale.

Write-through caching treats MongoDB as the system of record for quota state. Every interim-update write goes to Redis and MongoDB together. When Redis fails, the fallback reads MongoDB and gets the accurate current value immediately. The billing error window is zero — not 5 seconds, not 30 seconds: zero. The 2–5ms write overhead per update is the cost, and it is completely acceptable at our packet rate.

On recovery, Redis re-syncs from MongoDB automatically. No manual intervention, no reconciliation job, no Revenue Assurance ticket from a billing discrepancy."`} />;
}

function C8Interview() {
  return <Interview text={`"Per RFC 2866, BNG always sends cumulative octets since session start. At 300Mbps Service Provider Fiber speed, the 32-bit counter wraps every 1.8 minutes — in a 20-minute interval it wraps 11 times. RFC 2869 Gigawords handling is mandatory, not optional.

AAA Server calculates delta using the Gigawords-aware formula and proxies only the delta to OCS. Because we send relative deltas, not absolute totals, the OCS operation 'balance = balance - delta' is commutative — order doesn't matter, no row locks needed. OCS processes thousands of concurrent deductions without contention.

The previous cumulative total is stored in Redis for fast lookup, with write-through to MongoDB for failover safety. If Redis fails, the fallback reads MongoDB and the delta calculation remains correct. This is the same write-through pattern we use for session quota state."`} />;
}

function C9Interview() {
  return <Interview text={`"By Value is our most important performance decision. We actively ACCEPT eventual consistency for 90% of postpaid subscribers — that is a deliberate business choice, not a technical workaround. The 10% prepaid with low balances get synchronous OCS calls because we have zero tolerance for revenue leakage on prepaid.

Without this routing decision, we would need synchronous OCS calls for all 50 million subscribers. The OCS is designed for 5,000 synchronous calls per second — not 50,000. It would collapse immediately. By Value reduces OCS sync load by 90%, keeping it well within capacity while guaranteeing zero revenue leakage for the subscribers where it matters most.

The key insight: eventual consistency is perfectly acceptable for postpaid subscribers because they are billed monthly and any delta discrepancy reconciles at billing cycle close. Prepaid subscribers spend their own pre-loaded balance — every megabyte must be accounted for in real time."`} />;
}

function C10Interview() {
  return <Interview text={`"OCS response handling is where all the Flow 2 concepts converge. The OCS has four possible responses, and each demands a completely different action from AAA Server.

Normal ACK: do nothing, forward to BNG, wait for next interim. Threshold crossed at 50% or 90%: send an idempotent SMS notification using $addToSet — but critically, NO CoA, NO speed change. The subscriber still has data remaining. Quota exhausted at 100%: this is the enforcement path — update state to QUOTA_EXHAUSTED, send CoA Type 2 to BNG with FUP speed of 1Mbps, wait for CoA-ACK confirming hardware throttled, and only then publish the notification event for SMS.

The most dangerous confusion is between threshold and exhaustion. If you send a CoA at 50%, you have throttled a subscriber who still has half their data. If you skip enforcement at 100%, the subscriber uses data for free. And if you notify before CoA-ACK at 100%, you violate Pessimistic View — the SMS says 'throttled' but BNG may not have applied it yet.

OCS is the decision maker for all quota matters. AAA Server never independently decides to throttle — it reads the OCS signal via RADIUS accounting-response VSA attribute and enforces accordingly."`} />;
}

// ─────────────────────────────────────────────
// EXPORTED TAB SYSTEM
// ─────────────────────────────────────────────
const TABS_ALL = [
  { id: "problem",   label: "Problem Statement",   icon: "📋" },
  { id: "notes",     label: "Study Notes",          icon: "📖" },
  { id: "interview", label: "Interview Statement",  icon: "🎯" },
];

export function getConceptTabs() {
  return TABS_ALL;
}

export function ConceptTabContent({ conceptId, tabId }) {
  const concept = CONCEPTS.find(c => c.id === conceptId);
  if (!concept) return null;

  if (tabId === "problem") {
    return <ConceptProblem id={conceptId} color={concept.color} />;
  }

  if (tabId === "notes") {
    return (
      <div style={{ fontFamily: "'JetBrains Mono','Fira Code',monospace", color: "#e2e8f0", display: "flex", flexDirection: "column", gap: 10 }}>
        {conceptId === "c1"  && <C1Info />}
        {conceptId === "c2"  && <C2Info />}
        {conceptId === "c3"  && <C3Info />}
        {conceptId === "c4"  && <C4Info />}
        {conceptId === "c6"  && <C6Info />}
        {conceptId === "c7"  && <C7Info />}
        {conceptId === "c8"  && <C8Info />}
        {conceptId === "c9"  && <C9Info />}
        {conceptId === "c10" && <C10Info />}
      </div>
    );
  }

  if (tabId === "interview") {
    return (
      <div style={{ fontFamily: "'JetBrains Mono','Fira Code',monospace", color: "#e2e8f0" }}>
        {conceptId === "c1"  && <C1Interview />}
        {conceptId === "c2"  && <C2Interview />}
        {conceptId === "c3"  && <C3Interview />}
        {conceptId === "c4"  && <C4Interview />}
        {conceptId === "c6"  && <C6Interview />}
        {conceptId === "c7"  && <C7Interview />}
        {conceptId === "c8"  && <C8Interview />}
        {conceptId === "c9"  && <C9Interview />}
        {conceptId === "c10" && <C10Interview />}
      </div>
    );
  }

  return null;
}

// ─────────────────────────────────────────────
// INFO PANELS PER CONCEPT
// ─────────────────────────────────────────────
const InfoBox = ({ title, color, children }) => (
  <div style={{ background: "#080e1c", border: `1px solid ${color}33`, borderRadius: 8, padding: 14 }}>
    <div style={{ fontSize: 10, color, fontWeight: 700, letterSpacing: 1.5, marginBottom: 10, textTransform: "uppercase" }}>{title}</div>
    {children}
  </div>
);

const Row = ({ items }) => (
  <div style={{ display: "grid", gridTemplateColumns: `repeat(${items.length}, 1fr)`, gap: 10, marginBottom: 10 }}>
    {items}
  </div>
);

const Bullet = ({ color, items, bad }) => items.map((t, i) => (
  <div key={i} style={{ fontSize: 9.5, color: color + "cc", marginBottom: 4, display: "flex", gap: 6 }}>
    <span style={{ color, flexShrink: 0 }}>{bad ? "✗" : "✓"}</span>{t}
  </div>
));

const Interview = ({ text }) => (
  <div style={{
    background: "#0a1628", border: "1px solid #1e3a5f",
    borderRadius: 8, padding: 14,
  }}>
    <div style={{ fontSize: 10, color: "#3b82f6", fontWeight: 700, letterSpacing: 1.5, marginBottom: 8, textTransform: "uppercase" }}>
      🎯 Interview Statement
    </div>
    <div style={{ fontSize: 9.5, color: "#93c5fd", lineHeight: 1.75 }}>{text}</div>
  </div>
);

// ─────────────────────────────────────────────
// STUDY NOTES LAYOUT COMPONENTS
// ─────────────────────────────────────────────
const SH = ({ text, color }) => (
  <div style={{ fontSize: 11, fontWeight: 700, color, letterSpacing: 0.5, marginBottom: 10, paddingBottom: 6, borderBottom: `1px solid ${color}55` }}>
    ⚡ Solution: {text}
  </div>
);
const SP = ({ children, color }) => (
  <div style={{ background: "#0a1628", border: `1px solid ${color}55`, borderRadius: 8, padding: "10px 14px", marginBottom: 12, fontSize: 9.5, color: "#94a3b8", lineHeight: 1.85 }}>
    {children}
  </div>
);
const LBox = ({ title, color, items }) => (
  <div style={{ background: "#080e1c", border: `1px solid ${color}`, borderRadius: 8, padding: "10px 12px" }}>
    <div style={{ fontSize: 9.5, color, fontWeight: 700, marginBottom: 8 }}>{title}</div>
    {items.map((item, i) => (
      <div key={i} style={{ fontSize: 8.5, color: "#94a3b8", marginBottom: 4, display: "flex", gap: 6, lineHeight: 1.6 }}>
        <span style={{ color, flexShrink: 0 }}>•</span><span>{item}</span>
      </div>
    ))}
  </div>
);
const BAT = ({ before, after }) => (
  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 12 }}>
    <LBox title="❌ Before Solution" color="#ef4444" items={before} />
    <LBox title="✅ After Solution" color="#22c55e" items={after} />
  </div>
);
const PBox = ({ title, color, items }) => (
  <div style={{ background: "#080e1c", border: `1px solid ${color}`, borderRadius: 8, padding: "10px 12px", marginBottom: 10 }}>
    <div style={{ fontSize: 9.5, color, fontWeight: 700, marginBottom: 8 }}>{title}</div>
    {items.map((item, i) => (
      <div key={i} style={{ fontSize: 8.5, color: "#94a3b8", marginBottom: 5, display: "flex", gap: 6, lineHeight: 1.6 }}>
        <span style={{ color, flexShrink: 0 }}>→</span><span>{item}</span>
      </div>
    ))}
  </div>
);

function C1Interview() {
  return (
    <Interview text={`"The Semantic Lock prevents Lost Updates — but the deeper insight is the BUSINESS impact. Without it, PCF overwrites suspension, the user enjoys 150Mbps on an unpaid account, and critically — no compensating transaction is ever triggered.

The lock handles both race orderings: booster first → suspension 409s and retries. Suspension first → pre-check reads SUSPENDED and fails fast without sending any CoA to BNG — no hardware confusion.

Note: when suspension wins, it must also send a CoA to BNG — a database state change alone does not change hardware. We wait for that CoA-ACK before publishing the conflict event. Pessimistic View applied here too.

Between the booster CoA-ACK and the suspension CoA-ACK there is a brief window where the subscriber briefly gets 150Mbps on a suspended account. This is acceptable — the suspension CoA is sent immediately after the pivot decision, the window is milliseconds, and the alternative without a lock is permanent corruption.

This maps to Chapter 4: booster = Compensatable, suspension = Pivot, refund = Compensating Transaction."`} />
  );
}

function C1Info() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      <SH text="Semantic Lock: Flag record UPGRADE_IN_PROGRESS before CoA to block concurrent saga writes" color="#3b82f6" />
      <SP color="#3b82f6">
        Without a lock, two concurrent sagas (booster + suspension) both read <span style={{color:"#f59e0b"}}>ACTIVE</span> and silently overwrite each other — the suspended account gets 150Mbps with no refund ever triggered. The Semantic Lock atomically sets <span style={{color:"#3b82f6"}}>UPGRADE_IN_PROGRESS</span> so any concurrent operation either 409s and retries, or fails fast on a terminal state. Both race orderings resolve correctly.
      </SP>
      <BAT
        before={[
          "Both operations read state = ACTIVE simultaneously",
          "Suspension writes SUSPENDED first",
          "Booster CoA-ACK arrives → overwrites state back to ACTIVE",
          "Suspended account gets 150Mbps — BSS layer sees no conflict",
          "No compensating transaction (refund) ever triggered",
        ]}
        after={[
          "Booster sets UPGRADE_IN_PROGRESS lock before sending CoA",
          "Suspension reads IN_PROGRESS → returns 409 → retries after booster",
          "OR: Suspension wins → state=SUSPENDED → booster pre-check reads SUSPENDED → fails fast, no CoA sent",
          "Either race ordering produces a correct business outcome",
          "Refund triggered in conflict case after suspension CoA-ACK",
        ]}
      />
      <PBox title="⚙️ Extra Points" color="#f59e0b" items={[
        "Pre-check order: 1) Terminal state (SUSPENDED/TERMINATED)? → fail fast, no CoA  2) IN_PROGRESS? → 409 conflict, retry  3) ACTIVE? → acquire lock and proceed",
        "Chapter 4 mapping: Booster = Compensatable Transaction, Suspension = Pivot Transaction, Refund = Compensating Transaction",
        "When suspension wins: suspension must also send a CoA to BNG — DB state change alone does not change hardware",
        "Pessimistic View applied: conflict event published only after suspension CoA-ACK is received",
      ]} />
      <PBox title="💡 Take Away" color="#3b82f6" items={[
        "Brief speed window (ms) between booster CoA-ACK and suspension CoA-ACK is acceptable — permanent corruption without lock is far worse",
        "CRM resolution: Option 1 (most common) — full refund + deactivate booster. Option 2 — hold booster, activates when account restored",
        "The deeper insight: without the lock, no compensating transaction is EVER triggered — corruption is silent and permanent",
      ]} />
    </div>
  );
}

function C2Info() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      <SH text="Transactional Outbox: Write CoA command to MongoDB atomically with state change so delivery survives any pod crash" color="#06b6d4" />
      <SP color="#06b6d4">
        TTL watchdog alone cannot distinguish three crash scenarios. <span style={{color:"#ef4444"}}>Scenario C</span> is the most dangerous — pod crashes after CoA-ACK but before MongoDB commit. BNG hardware is already upgraded but the watchdog marks state = <span style={{color:"#ef4444"}}>FAILED</span> and refunds an active plan. The Outbox writes the CoA payload atomically with the state change, making delivery retryable independent of pod lifecycle.
      </SP>
      <BAT
        before={[
          "Scenario A: crash before CoA sent → watchdog recovers ✓",
          "Scenario B: crash waiting for CoA-ACK → watchdog recovers ✓",
          "Scenario C: crash after CoA-ACK, before MongoDB commit → BNG at 150Mbps, watchdog marks FAILED, refunds active plan ✗",
          "Watchdog cannot detect which scenario occurred — treats all as Scenario A",
          "Billing error: user refunded for plan actively running on BNG hardware",
        ]}
        after={[
          "Outbox row (CoA payload) written atomically with UPGRADE_IN_PROGRESS state",
          "Relay polls outbox and sends CoA — retries on any failure",
          "On CoA-ACK: relay marks outbox SENT, state updated to ACTIVE",
          "Scenario C crash: watchdog marks outbox ABANDONED → relay ignores stale row — no false refund",
          "All 3 crash scenarios handled safely and automatically",
        ]}
      />
      <PBox title="⚙️ Extra Points" color="#f59e0b" items={[
        "Selective application: Flow 1 (user on Processing screen) = suitable, relay lag acceptable. Flow 2 (RADIUS sub-500ms) = NOT suitable, polling relay lag unacceptable",
        "Outbox status machine: PENDING (written with lock) → SENT (CoA-ACK received) → FAILED (max retries → trigger refund) / ABANDONED (watchdog fired → relay ignores)",
        "When watchdog fires it also marks related outbox rows ABANDONED — prevents stale CoA delivery from a resuming relay",
        "Residual edge case: Scenario C + relay failure → divergence detected by reconciliation job (≤5min) or next RADIUS Interim-Update",
      ]} />
      <PBox title="💡 Take Away" color="#06b6d4" items={[
        "Billing discrepancies always require human intervention — automated job fixes the network, only Revenue Assurance determines the correct financial outcome",
        "Outbox guarantees at-least-once CoA delivery — idempotency at BNG prevents duplicate hardware changes",
        "The outbox pattern is applied selectively, not universally — latency profile of each flow determines suitability",
      ]} />
    </div>
  );
}

function C3Info() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      <SH text="@Version Optimistic Lock: Detect concurrent GR writes after CoA-ACK and send corrective CoA if hardware drifted from database" color="#f59e0b" />
      <SP color="#f59e0b">
        AAA Server runs Active-Active GR across two data centres with <span style={{color:"#f59e0b"}}>20–40ms MongoDB replication lag</span>. When Gujarat's save throws <span style={{color:"#ef4444"}}>OptimisticLockingException</span> after CoA-ACK, hardware is already upgraded. Simply re-saving would silently overwrite Mumbai's admin change — network and database would diverge permanently with no error and no alert.
      </SP>
      <BAT
        before={[
          "Gujarat holds @Version=5, sends CoA to upgrade to 150Mbps",
          "Admin in Mumbai emergency-downgrades: saves @Version=6, speed=100Mbps",
          "Gujarat receives CoA-ACK, blindly re-saves @Version=5, speed=150Mbps",
          "Admin's change silently overwritten — no error, no alert raised",
          "Network runs 150Mbps, database says 100Mbps — permanent invisible corruption",
        ]}
        after={[
          "OptimisticLockingException thrown on Gujarat's stale @Version=5 save",
          "Re-read fresh MongoDB state",
          "QUOTA_EXHAUSTED? → OCS is authority, no corrective CoA, just re-save with fresh @Version",
          "ACTIVE + speed differs? → admin changed during CoA window → send corrective CoA to realign hardware",
          "ACTIVE + speed matches? → safe to re-save with fresh @Version only",
        ]}
      />
      <PBox title="⚙️ Extra Points" color="#06b6d4" items={[
        "CoA-ACK is the pivot point — once BNG confirms, hardware is already changed. Cannot just re-read and save blindly",
        "OCS is the quota authority — never send corrective CoA that overrides an OCS throttle (QUOTA_EXHAUSTED state takes priority over admin speed)",
        "All corrective CoA activity must be fully logged, ops team alerted, and rate-limited per hour",
      ]} />
      <PBox title="💡 Take Away" color="#f59e0b" items={[
        "Network state MUST always equal database state — this is a carrier-grade non-negotiable requirement",
        "Human oversight = monitoring and alerting, not manual approval per subscriber (impossible at 50M scale)",
        "The 3-case state-first check is the key insight: not just 'retry' but 'what does the hardware need to do right now?'",
      ]} />
    </div>
  );
}

function C4Info() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      <SH text="Pessimistic View: Hold all downstream notifications until BNG hardware confirms via CoA-ACK" color="#a855f7" />
      <SP color="#a855f7">
        Optimistic notification — telling the app "success" before BNG confirms — is the <span style={{color:"#ef4444"}}>#1 cause of subscriber complaints</span> in broadband. If BNG reboots after the DB write but before CoA-ACK, the subscriber's app shows 150Mbps but the network delivers 50Mbps. Pessimistic View holds every downstream event (CRM, SMS, notifications) until CoA-ACK is physically received from BNG.
      </SP>
      <BAT
        before={[
          "BoosterSuccessEvent published immediately after DB write",
          "App shows 'Activated — 150Mbps' to subscriber",
          "BNG reboots, CoA is lost, hardware stays at 50Mbps",
          "Subscriber complains — #1 cause of broadband support calls",
          "FUP: SMS says 'throttled' before BNG confirms enforcement",
        ]}
        after={[
          "BoosterSuccessEvent held until CoA-ACK received from BNG",
          "User sees 'Processing…' for a few seconds",
          "Success shown only when hardware physically confirms",
          "FUP throttle SMS sent only after throttle CoA-ACK received",
          "Zero false promises — trust maintained at every interaction",
        ]}
      />
      <PBox title="⚙️ Extra Points" color="#0ea5e9" items={[
        "Flow 1: BoosterSuccessEvent (booster) and HardwareUpgradeConflictEvent (conflict) — both wait for CoA-ACK before publishing",
        "Flow 2 at 100%: ThresholdCrossedEvent notification held until throttle CoA-ACK confirmed by BNG hardware",
        "OCS = Decision Maker (evaluates quota, signals via RADIUS VSA attribute). AAA = Enforcer (sends CoA on OCS signal). BNG = Hardware owner (confirms via CoA-ACK)",
        "AAA Server NEVER independently decides to throttle — always reads and follows OCS instruction via VSA attribute",
      ]} />
      <PBox title="💡 Take Away" color="#a855f7" items={[
        "Cost: a few seconds of 'Processing…' on the screen",
        "Gain: zero false promises, no support escalations, subscriber trust maintained permanently",
        "The OCS→AAA→BNG role separation is a carrier-grade architecture principle — no layer bypasses its designated role",
      ]} />
    </div>
  );
}

// ─────────────────────────────────────────────
// C6 INFO: Reactive Event Loop
// ─────────────────────────────────────────────
function C6Info() {
  return (
    <div style={{ display:"flex", flexDirection:"column", gap:4 }}>
      <SH text="Reactive MongoDB: Replace blocking MongoTemplate with ReactiveMongoTemplate so Netty threads never park on I/O" color="#f43f5e" />
      <SP color="#f43f5e">
        Spring WebFlux runs on Netty's event loop — <span style={{color:"#f43f5e"}}>16 threads handling 50,000 RADIUS packets/sec</span>. A single <span style={{color:"#ef4444"}}>blocking mongoTemplate.findOne()</span> parks one thread on kernel I/O wait. When 16 packets arrive simultaneously, all 16 threads park — the event loop is completely starved. UDP socket buffers fill, packets drop, RFC 2865 clients retransmit, and the retransmit storm compounds into complete throughput collapse.
      </SP>
      <BAT
        before={[
          "MongoTemplate.findOne() called inside WebFlux handler",
          "Thread blocks on kernel I/O wait for MongoDB response (~5–50ms)",
          "16 simultaneous packets → all 16 Netty threads parked",
          "Zero capacity to accept new RADIUS packets",
          "UDP socket buffer fills → kernel drops packets → retransmit storm → complete collapse",
        ]}
        after={[
          "ReactiveMongoTemplate.findOne() returns Mono<> in under 1ms",
          "Netty thread released immediately back to event loop pool",
          "MongoDB I/O runs on the driver's dedicated thread pool",
          "Callback fires on whichever Netty thread is free when I/O completes",
          "50,000 packets/sec sustained — all 16 threads continuously available",
        ]}
      />
      <PBox title="⚙️ Extra Points" color="#f59e0b" items={[
        "@Blocking annotation: Spring throws a startup error if a @Blocking method is called inside a reactive context — caught at build time, never reaches production",
        "BlockHound in test suite: fails immediately if any test path contains Thread.sleep() or blocking I/O inside a reactive chain",
        "Never block inside subscribe() callback — it runs on the Netty thread and has the identical starvation risk as the handler itself",
      ]} />
      <PBox title="💡 Take Away" color="#f43f5e" items={[
        "This is NOT an architectural change — it is a single import swap from MongoTemplate to ReactiveMongoTemplate",
        "Blocking calls in a WebFlux reactive pipeline = most common root cause of carrier-grade throughput collapse",
        "50K pkt/sec with 16 threads is only possible because each handler returns in microseconds — any thread parking breaks this entirely",
      ]} />
    </div>
  );
}

// ─────────────────────────────────────────────
// C7 INFO: Redis Failover Fallback
// ─────────────────────────────────────────────
function C7Info() {
  return (
    <div style={{ display:"flex", flexDirection:"column", gap:4 }}>
      <SH text="Write-Through Cache: Write quota_used to Redis AND MongoDB together so any Redis outage falls back to accurate MongoDB state immediately" color="#f59e0b" />
      <SP color="#f59e0b">
        Redis provides <span style={{color:"#f59e0b"}}>sub-millisecond quota reads at 50,000 packets/sec</span>, but it is volatile — a rolling cluster upgrade, network partition, or memory eviction drops state in seconds. Without a fallback, a cache miss <span style={{color:"#ef4444"}}>defaults quota_used = 0</span>, making the subscriber appear to have a fresh full quota. The subscriber accumulates 10GB unmetered: operator delivers 18GB, bills for 10GB — silent revenue leakage.
      </SP>
      <BAT
        before={[
          "Redis holds quota_used = 8GB for active subscriber",
          "Redis rolling upgrade → 5–30 sec cluster outage",
          "RADIUS Interim-Update arrives → cache lookup → connection timeout",
          "AAA defaults quota_used = 0 — subscriber appears to have full quota",
          "BNG told to continue — subscriber uses 10GB unmetered → CDR: 10GB billed, 18GB delivered",
        ]}
        after={[
          "Every quota update written to Redis + MongoDB simultaneously (~2–5ms overhead)",
          "Redis outage → cache miss → immediately fall back to MongoDB read",
          "MongoDB holds accurate quota_used from all prior write-through writes",
          "Correct quota enforcement applied — zero revenue leakage",
          "Redis recovers → re-syncs from MongoDB automatically, no manual intervention",
        ]}
      />
      <PBox title="⚙️ Extra Points" color="#06b6d4" items={[
        "Redis = speed (sub-1ms, 50K reads/sec). MongoDB = truth (survives any Redis failure, always current from write-through)",
        "Delta safety check: if MongoDB quota_used > new reported value, discard — prevents negative delta billing from stale CDRs",
        "Redis Sentinel auto-failover reduces window to <5 sec but does NOT eliminate the gap — write-through is still required for safety",
      ]} />
      <PBox title="💡 Take Away" color="#f59e0b" items={[
        "Billing error window with write-through: ZERO — not 5 seconds, not 30 seconds: zero",
        "Write-through overhead: ~2–5ms per interim update — completely acceptable at carrier packet rates",
        "This exact write-through + MongoDB-fallback pattern is reused for prevTotal storage in Gigawords handling (C8)",
      ]} />
    </div>
  );
}

// ─────────────────────────────────────────────
// C8 INFO: Commutative Updates
// ─────────────────────────────────────────────
function C8Info() {
  return (
    <div style={{ display:"flex", flexDirection:"column", gap:4 }}>
      <SH text="RFC 2869 Gigawords: Combine 32-bit Octet counter with Gigawords for true 64-bit total; send relative deltas to OCS for lock-free commutative deductions" color="#e879f9" />
      <SP color="#e879f9">
        RADIUS <span style={{color:"#e879f9"}}>Acct-Output-Octets is a 32-bit counter</span> — max 4GB. At 300Mbps it wraps every 1.8 minutes. Without Gigawords, the post-wrap counter (200MB) minus the pre-wrap total (3,800MB) gives a large <span style={{color:"#ef4444"}}>negative number, clamped to zero</span> — OCS receives zero usage and the subscriber uses data for free. With Gigawords, the true 64-bit total is always correct.
      </SP>
      <BAT
        before={[
          "BNG sends cumulative Acct-Output-Octets (32-bit): wraps at 4,294,967,295 bytes",
          "At 300Mbps: wraps every 1.8 min — 11 wraps in a 20-min interval",
          "After wrap: prevTotal=3,800MB, currOctets=200MB",
          "delta = 200M − 3,800M = −3.6B → clamped to 0",
          "OCS receives 0 usage → no deduction → subscriber uses 700MB free",
        ]}
        after={[
          "trueTotal = (Gigawords × 4,294,967,296) + Octets",
          "Delta is always positive and correct regardless of wrap count",
          "AAA sends relative delta to OCS: balance = balance − delta",
          "Delta operation is commutative — order-independent, no row locks at OCS",
          "Thousands of concurrent OCS deductions process without contention",
        ]}
      />
      <PBox title="⚙️ Extra Points" color="#06b6d4" items={[
        "Counter wrap frequency: 30Mbps → 18min, 100Mbps → 5.5min, 300Mbps → 1.8min, 1Gbps → 32sec — mandatory handling at SP Fiber speeds",
        "prevTotal stored in Redis (fast lookup) with write-through to MongoDB (failover) — same pattern as session quota cache (C7)",
        "Negative delta after Gigawords math = BNG restart or session reset → discard and treat as new session start",
      ]} />
      <PBox title="💡 Take Away" color="#e879f9" items={[
        "RFC 2869 Gigawords is mandatory, not optional — at SP Fiber speeds, 32-bit counter wraps constantly",
        "Commutative delta design eliminates row locking at OCS — this is the key to carrier-scale OCS throughput",
        "Two independent problems solved together: overflow correctness (Gigawords) and concurrency safety (relative deltas)",
      ]} />
    </div>
  );
}

// ─────────────────────────────────────────────
// C9 INFO: By Value — Risk-Based Routing
// ─────────────────────────────────────────────
function C9Info() {
  return (
    <div style={{ display:"flex", flexDirection:"column", gap:4 }}>
      <SH text="By Value Routing: Route 10% prepaid low-balance to sync OCS; 90% postpaid to async Kafka CDR — 10× OCS throughput improvement" color="#8b5cf6" />
      <SP color="#8b5cf6">
        Without routing differentiation, all <span style={{color:"#ef4444"}}>50M subscribers require synchronous OCS calls</span> on every RADIUS Interim-Update. OCS is designed for 5,000 sync calls/sec — at 50,000/sec it collapses immediately. By Value reads the subscriber profile and routes by actual risk: prepaid with low balance has zero revenue leakage tolerance; postpaid reconciles at billing cycle close.
      </SP>
      <BAT
        before={[
          "All 50M subscribers → synchronous RADIUS proxy to OCS on every Interim-Update",
          "OCS designed for 5,000 sync calls/sec",
          "Actual load: 50,000 calls/sec → OCS becomes the bottleneck",
          "Complete system collapse — no prepaid/postpaid distinction made",
          "No separation between revenue-risk and billing-safe subscribers",
        ]}
        after={[
          "isHighRisk(): PREPAID + balanceBytes < 10GB → ~10% of 50M subscribers",
          "High risk → sync RADIUS proxy to OCS → real-time enforcement, zero revenue leakage",
          "Low risk → async Kafka → CDR system deducts at billing cycle close",
          "OCS sync load reduced to 5,000/sec — within designed capacity",
          "10× throughput improvement — sustainable at carrier scale",
        ]}
      />
      <PBox title="⚙️ Extra Points" color="#0ea5e9" items={[
        "isHighRisk() logic: planType.equals(\"PREPAID\") && balanceBytes < TEN_GB; SubscriberProfile read from MongoDB before routing decision",
        "Postpaid → Kafka: fire-and-forget, AAA thread free in under 1ms, CDR system processes offline at billing cycle close",
        "Prepaid → RADIUS proxy: synchronous, AAA thread held 50–200ms, but only 10% of total traffic so OCS stays within capacity",
        "OCS response for the high-risk path drives further action: Normal ACK, threshold 50%/90%, or quota exhausted 100% — handled by C10",
      ]} />
      <PBox title="💡 Take Away" color="#8b5cf6" items={[
        "We ACTIVELY ACCEPT eventual consistency for 90% of subscribers — this is a deliberate business strategy, not a technical workaround",
        "Prepaid subscribers spend their own pre-loaded balance — every megabyte must be accounted for in real time",
        "This routing decision reduces OCS load from 50K/sec (collapse) to 5K/sec (sustainable) — a 10× improvement with one classification check",
      ]} />
    </div>
  );
}

// ─────────────────────────────────────────────
// C10 INFO: OCS Response Handling
// ─────────────────────────────────────────────
function C10Info() {
  return (
    <div style={{ display:"flex", flexDirection:"column", gap:4 }}>
      <SH text="Four Distinct Response Paths: ACK = wait, Threshold = notify only, Exhausted = enforce then notify, Async = skip — never confuse threshold with exhaustion" color="#0ea5e9" />
      <SP color="#0ea5e9">
        OCS returns four signals after quota evaluation. Each requires a completely different action from AAA Server. The most dangerous confusion is <span style={{color:"#f59e0b"}}>threshold (50%/90%) vs exhaustion (100%)</span> — threshold is <span style={{color:"#f59e0b"}}>notify-only with NO CoA</span>, while exhaustion requires <span style={{color:"#ef4444"}}>CoA enforcement before any notification</span>. Swapping these causes false throttle at 50% or unmetered usage at 100%.
      </SP>
      <BAT
        before={[
          "All OCS non-ACK responses treated the same",
          "CoA sent at 50% threshold → subscriber throttled with half quota remaining",
          "OR: No CoA sent at 100% → subscriber uses data past quota for free",
          "Notification published before CoA-ACK → violates Pessimistic View",
          "SMS says 'throttled' but BNG hardware may not have applied it yet",
        ]}
        after={[
          "Normal ACK: forward to BNG, wait for next Interim-Update, nothing else",
          "Threshold 50%/90%: idempotent $addToSet SMS only — NO CoA, NO speed change",
          "Quota Exhausted 100%: state=QUOTA_EXHAUSTED → CoA Type 2 (1Mbps) → wait CoA-ACK → THEN publish SMS event",
          "Async postpaid path: Mono.empty() — no OCS response to handle",
          "Each path completely isolated — no cross-contamination between response types",
        ]}
      />
      <PBox title="⚙️ Extra Points" color="#f59e0b" items={[
        "$addToSet on threshold notifications: exactly-once SMS per threshold level — idempotent on retries and on duplicate OCS signals",
        "Quota Exhausted path applies Pessimistic View: SMS notification event published only AFTER throttle CoA-ACK received from BNG",
        "CoA Type 2 FUP speed (1Mbps) is defined by operator policy per subscriber plan, not hardcoded in AAA Server logic",
      ]} />
      <PBox title="💡 Take Away" color="#0ea5e9" items={[
        "OCS = Decision Maker for all quota matters. AAA Server = Enforcer. BNG = Hardware owner. AAA never independently throttles",
        "All four Flow 2 concepts converge here: By Value routing (C9), Pessimistic View (C4), Commutative deltas (C8), Redis cache fallback (C7)",
        "The $addToSet idempotency guard at threshold is easy to miss and critical — without it, subscribers get duplicate SMSes on every retry",
      ]} />
    </div>
  );
}
