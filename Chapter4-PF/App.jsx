import { useState, useEffect } from "react";
import DiagramApp, { getConceptTabs, ConceptTabContent } from "./concept1.jsx";

// ─────────────────────────────────────────────
// THEME
// ─────────────────────────────────────────────
const F = "'JetBrains Mono','Fira Code',monospace";
const C = {
  bg0:"#050a14", bg1:"#080e1c", bg2:"#060c18", bg3:"#0d1a30",
  border:"#0f1e38", text:"#e2e8f0", textMuted:"#475569", textDim:"#334155",
  accent:"#3b82f6", accentDim:"#1d3a6e", green:"#22c55e",
};

// ─────────────────────────────────────────────
// SEED DATA
// ─────────────────────────────────────────────
const SEED = {
  books: [{
    id:"b1", title:"Microservices Patterns", author:"Chris Richardson",
    chapters: [{
      id:"ch1", title:"Chapter 4: Managing Transactions with Sagas",
      concepts: [
        { id:"co1", title:"Semantic Lock",                  diagramId:"c1", domain:"Distributed Systems", completed:false },
        { id:"co2", title:"Countermeasure: Optimistic Lock", diagramId:"c2", domain:"Database Patterns",   completed:false },
        { id:"co3", title:"Pessimistic View",               diagramId:"c3", domain:"Distributed Systems", completed:false },
        { id:"co4", title:"Re-read Value",                  diagramId:"c4", domain:"Database Patterns",   completed:false },
        { id:"co5", title:"Version File",                   diagramId:"c5", domain:"Database Patterns",   completed:false },
        { id:"co6", title:"By Value",                       diagramId:"c6", domain:"Policy Control",      completed:false },
        { id:"co7", title:"Redis Session Cache",            diagramId:"c7", domain:"Caching / Redis",     completed:false },
        { id:"co8", title:"Gigawords Handling",             diagramId:"c8", domain:"RADIUS / RFC",        completed:false },
        { id:"co9", title:"OCS Response Handling",          diagramId:"c9", domain:"OCS / Billing",       completed:false },
      ],
    }],
  }],
};

// ─────────────────────────────────────────────
// PERSISTENCE
// ─────────────────────────────────────────────
const LS_KEY = "learning-app-v2";
function load() {
  try { const s = localStorage.getItem(LS_KEY); return s ? JSON.parse(s) : SEED; }
  catch { return SEED; }
}
function save(d) {
  try { localStorage.setItem(LS_KEY, JSON.stringify(d)); } catch {}
}

// ─────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────
function uid() { return Math.random().toString(36).slice(2, 9); }

// ─────────────────────────────────────────────
// MAIN APP
// ─────────────────────────────────────────────
export default function LearningApp() {
  const [data,         setData]         = useState(load);
  const [selBookId,    setSelBookId]    = useState(null);
  const [selChapId,    setSelChapId]    = useState(null);
  const [selConceptId, setSelConceptId] = useState(null);
  const [sidebarOpen,  setSidebarOpen]  = useState(true);
  const [expBooks,     setExpBooks]     = useState({ b1: true });
  const [expChaps,     setExpChaps]     = useState({ ch1: true });

  useEffect(() => { save(data); }, [data]);

  // ── Finders ──
  function findBook(id)    { return data.books.find(b => b.id === id); }
  function findChap(id)    { for (const b of data.books) { const c = b.chapters.find(c => c.id === id); if (c) return c; } return null; }
  function findConcept(id) { for (const b of data.books) for (const ch of b.chapters) { const co = ch.concepts.find(c => c.id === id); if (co) return co; } return null; }

  // ── Mutators ──
  function updateConcept(id, patch) {
    setData(d => ({ ...d, books: d.books.map(b => ({
      ...b, chapters: b.chapters.map(ch => ({
        ...ch, concepts: ch.concepts.map(co => co.id === id ? { ...co, ...patch } : co),
      })),
    }))}));
  }

  function addBook() {
    const title = prompt("Book title:"); if (!title) return;
    const author = prompt("Author:") || "";
    setData(d => ({ ...d, books: [...d.books, { id: uid(), title, author, chapters: [] }] }));
  }

  function addChapter(bookId) {
    const title = prompt("Chapter title:"); if (!title) return;
    const id = uid();
    setData(d => ({ ...d, books: d.books.map(b => b.id === bookId
      ? { ...b, chapters: [...b.chapters, { id, title, concepts: [] }] } : b
    )}));
    setExpChaps(e => ({ ...e, [id]: true }));
  }

  function addConcept(chapId) {
    const title = prompt("Concept title:"); if (!title) return;
    setData(d => ({ ...d, books: d.books.map(b => ({
      ...b, chapters: b.chapters.map(ch => ch.id === chapId
        ? { ...ch, concepts: [...ch.concepts, { id: uid(), title, diagramId: null, domain: "Other", completed: false }] }
        : ch),
    }))}));
  }

  function toggleComplete(coId, e) {
    if (e) e.stopPropagation();
    const co = findConcept(coId);
    if (co) updateConcept(coId, { completed: !co.completed });
  }

  // ── Concept selection: auto-collapse sidebar ──
  function selectConcept(coId, chapId, bookId) {
    setSelConceptId(coId);
    setSelChapId(chapId);
    setSelBookId(bookId);
    setSidebarOpen(false);
  }

  // ── Breadcrumb navigation ──
  function goLibrary() { setSelBookId(null); setSelChapId(null); setSelConceptId(null); setSidebarOpen(true); }
  function goBook(id)  { setSelBookId(id);   setSelChapId(null); setSelConceptId(null); setSidebarOpen(true); }
  function goChap(chapId, bookId) { setSelBookId(bookId); setSelChapId(chapId); setSelConceptId(null); setSidebarOpen(true); }

  const selBook    = findBook(selBookId);
  const selChap    = findChap(selChapId);
  const selConcept = findConcept(selConceptId);

  // ── Breadcrumb ──
  const crumbs = [
    { label: "Library",         onClick: goLibrary },
    selBook    && { label: selBook.title,    onClick: () => goBook(selBookId) },
    selChap    && { label: selChap.title,    onClick: () => goChap(selChapId, selBookId) },
    selConcept && { label: selConcept.title, onClick: null },
  ].filter(Boolean);

  // ─────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────
  return (
    <div style={{ background: C.bg0, height: "100vh", fontFamily: F, color: C.text, display: "flex", flexDirection: "column", overflow: "hidden" }}>

      {/* ── HEADER ── */}
      <div style={{ background: C.bg1, borderBottom: `1px solid ${C.border}`, padding: "0 20px", height: 48, display: "flex", alignItems: "center", gap: 14, flexShrink: 0 }}>
        {/* Hamburger */}
        <button
          onClick={() => setSidebarOpen(o => !o)}
          style={{ background: "none", border: "none", color: C.textMuted, cursor: "pointer", padding: 4, display: "flex", flexDirection: "column", gap: 4, flexShrink: 0 }}
          title={sidebarOpen ? "Close menu" : "Open menu"}
        >
          <span style={{ display: "block", width: 18, height: 2, background: C.textMuted, borderRadius: 1 }} />
          <span style={{ display: "block", width: 18, height: 2, background: C.textMuted, borderRadius: 1 }} />
          <span style={{ display: "block", width: 18, height: 2, background: C.textMuted, borderRadius: 1 }} />
        </button>

        <div style={{ fontSize: 14, fontWeight: 700, letterSpacing: 0.5, cursor: "pointer", userSelect: "none" }} onClick={goLibrary}>
          📚 MY LEARNING APP
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ fontSize: 10, color: C.textDim, letterSpacing: 2, textTransform: "uppercase" }}>Knowledge Library</div>
      </div>

      {/* ── BREADCRUMB ── */}
      <div style={{ background: C.bg2, borderBottom: `1px solid ${C.border}`, padding: "7px 20px", display: "flex", alignItems: "center", gap: 6, fontSize: 11, color: C.textMuted, flexShrink: 0 }}>
        {crumbs.map((cr, i) => (
          <span key={i} style={{ display: "flex", alignItems: "center", gap: 6 }}>
            {i > 0 && <span style={{ color: C.textDim }}>›</span>}
            {cr.onClick
              ? <span onClick={cr.onClick} style={{ cursor: "pointer", color: C.accent }}>{cr.label}</span>
              : <span style={{ color: C.text, fontWeight: 600 }}>{cr.label}</span>
            }
          </span>
        ))}
      </div>

      {/* ── BODY: sidebar + main ── */}
      <div style={{ flex: 1, display: "flex", overflow: "hidden", minHeight: 0 }}>

        {/* ── SIDEBAR ── */}
        {sidebarOpen && (
          <div style={{ width: 252, background: C.bg1, borderRight: `1px solid ${C.border}`, display: "flex", flexDirection: "column", overflow: "hidden", flexShrink: 0 }}>
            <div style={{ flex: 1, overflowY: "auto" }}>
              {data.books.map(b => {
                const bTot  = b.chapters.reduce((s, ch) => s + ch.concepts.length, 0);
                const bDone = b.chapters.reduce((s, ch) => s + ch.concepts.filter(co => co.completed).length, 0);
                const bExp  = expBooks[b.id];
                return (
                  <div key={b.id}>
                    {/* Book row */}
                    <div
                      onClick={() => setExpBooks(e => ({ ...e, [b.id]: !e[b.id] }))}
                      style={{ padding: "10px 14px", cursor: "pointer", display: "flex", alignItems: "center", gap: 8, borderBottom: `1px solid ${C.border}` }}
                    >
                      <span style={{ fontSize: 10, color: C.textDim, width: 10 }}>{bExp ? "▼" : "▶"}</span>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 11, fontWeight: 700, color: C.text, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{b.title}</div>
                        <div style={{ fontSize: 9, color: C.textDim }}>{b.author}</div>
                      </div>
                      <span style={{ fontSize: 9, color: bDone === bTot && bTot > 0 ? C.green : C.textDim, flexShrink: 0 }}>{bDone}/{bTot}</span>
                    </div>

                    {/* Chapters */}
                    {bExp && b.chapters.map(ch => {
                      const chDone = ch.concepts.filter(co => co.completed).length;
                      const chTot  = ch.concepts.length;
                      const chExp  = expChaps[ch.id];
                      return (
                        <div key={ch.id}>
                          {/* Chapter row */}
                          <div
                            onClick={() => setExpChaps(e => ({ ...e, [ch.id]: !e[ch.id] }))}
                            style={{ padding: "8px 14px 8px 26px", cursor: "pointer", display: "flex", alignItems: "center", gap: 6, background: selChapId === ch.id && !selConceptId ? C.bg3 : "transparent" }}
                          >
                            <span style={{ fontSize: 9, color: C.textDim, width: 10 }}>{chExp ? "▼" : "▶"}</span>
                            <span style={{ flex: 1, fontSize: 11, color: selChapId === ch.id ? C.text : C.textMuted, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{ch.title}</span>
                            <span style={{ fontSize: 9, color: chDone === chTot && chTot > 0 ? C.green : C.textDim, flexShrink: 0 }}>{chDone}/{chTot}</span>
                          </div>

                          {/* Concepts */}
                          {chExp && (
                            <div style={{ paddingBottom: 4 }}>
                              {ch.concepts.map(co => {
                                const isSel = co.id === selConceptId;
                                return (
                                  <div
                                    key={co.id}
                                    onClick={() => selectConcept(co.id, ch.id, b.id)}
                                    style={{
                                      padding: "6px 14px 6px 40px", cursor: "pointer", display: "flex", alignItems: "center", gap: 8,
                                      background: isSel ? C.accentDim : "transparent",
                                      borderLeft: isSel ? `2px solid ${C.accent}` : "2px solid transparent",
                                    }}
                                  >
                                    <span
                                      onClick={e => toggleComplete(co.id, e)}
                                      style={{ fontSize: 13, color: co.completed ? C.green : C.textDim, flexShrink: 0 }}
                                    >{co.completed ? "✔" : "○"}</span>
                                    <span style={{ fontSize: 11, color: isSel ? C.text : C.textMuted, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{co.title}</span>
                                  </div>
                                );
                              })}
                              <div style={{ padding: "4px 14px 4px 40px" }}>
                                <button
                                  onClick={() => addConcept(ch.id)}
                                  style={{ background: "none", border: `1px dashed ${C.textDim}`, color: C.textDim, borderRadius: 4, padding: "3px 8px", cursor: "pointer", fontFamily: F, fontSize: 10 }}
                                >＋ Concept</button>
                              </div>
                            </div>
                          )}
                        </div>
                      );
                    })}

                    {/* Add Chapter */}
                    {bExp && (
                      <div style={{ padding: "4px 14px 8px 26px" }}>
                        <button
                          onClick={() => addChapter(b.id)}
                          style={{ background: "none", border: `1px dashed ${C.textDim}`, color: C.textDim, borderRadius: 4, padding: "3px 8px", cursor: "pointer", fontFamily: F, fontSize: 10 }}
                        >＋ Chapter</button>
                      </div>
                    )}
                  </div>
                );
              })}

              {/* Add Book */}
              <div style={{ padding: "10px 14px" }}>
                <button
                  onClick={addBook}
                  style={{ background: "none", border: `1px dashed ${C.textDim}`, color: C.textDim, borderRadius: 4, padding: "4px 10px", cursor: "pointer", fontFamily: F, fontSize: 10, width: "100%" }}
                >＋ Add Book</button>
              </div>
            </div>
          </div>
        )}

        {/* ── MAIN AREA ── */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", minHeight: 0 }}>
          {selConcept ? (
            <ConceptView concept={selConcept} onToggleComplete={e => toggleComplete(selConcept.id, e)} />
          ) : (
            <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", color: C.textDim, fontSize: 13 }}>
              {sidebarOpen ? "Select a concept from the menu" : (
                <div style={{ textAlign: "center" }}>
                  <div style={{ marginBottom: 12 }}>No concept selected</div>
                  <button
                    onClick={() => setSidebarOpen(true)}
                    style={{ background: C.bg3, border: `1px solid ${C.border}`, color: C.textMuted, borderRadius: 6, padding: "6px 14px", cursor: "pointer", fontFamily: F, fontSize: 11 }}
                  >☰ Open Menu</button>
                </div>
              )}
            </div>
          )}
        </div>

      </div>
    </div>
  );
}

// ─────────────────────────────────────────────
// CONCEPT VIEW — diagram left + accordion right
// ─────────────────────────────────────────────
function ConceptView({ concept, onToggleComplete }) {
  const tabs = concept.diagramId ? getConceptTabs(concept.diagramId) : [];
  const [openId, setOpenId] = useState(() => tabs[0]?.id ?? null);

  // Reset when concept changes
  useEffect(() => {
    const t = concept.diagramId ? getConceptTabs(concept.diagramId) : [];
    setOpenId(t[0]?.id ?? null);
  }, [concept.id]); // eslint-disable-line

  function toggle(id) {
    setOpenId(prev => (prev === id ? null : id));
  }

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", minHeight: 0 }}>

      {/* ── Title bar ── */}
      <div style={{ background: C.bg1, borderBottom: `1px solid ${C.border}`, padding: "10px 20px", display: "flex", alignItems: "center", justifyContent: "space-between", flexShrink: 0 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontSize: 14, fontWeight: 700, color: C.text }}>{concept.title}</span>
          <span style={{ fontSize: 9, background: C.bg3, border: `1px solid ${C.border}`, color: C.textMuted, borderRadius: 4, padding: "2px 7px" }}>{concept.domain}</span>
        </div>
        <button
          onClick={onToggleComplete}
          style={{
            background: concept.completed ? C.greenDim : C.bg3,
            border: `1px solid ${concept.completed ? C.green : C.border}`,
            color: concept.completed ? C.green : C.textMuted,
            borderRadius: 6, padding: "4px 12px", cursor: "pointer", fontFamily: F, fontSize: 10,
          }}
        >{concept.completed ? "✔ Completed" : "○ Mark done"}</button>
      </div>

      {/* ── Body ── */}
      <div style={{ flex: 1, display: "flex", overflow: "hidden", minHeight: 0 }}>

        {/* Left: Diagram */}
        <div style={{ flex: 1, overflow: "auto", borderRight: `1px solid ${C.border}` }}>
          {concept.diagramId
            ? <DiagramApp initialConceptId={concept.diagramId} />
            : <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%", color: C.textDim, fontSize: 12 }}>No diagram linked.</div>
          }
        </div>

        {/* Right: Accordion */}
        {tabs.length > 0 && (
          <div style={{ width: 340, display: "flex", flexDirection: "column", overflow: "hidden", background: C.bg0, flexShrink: 0 }}>
            {tabs.map(t => {
              const isOpen = openId === t.id;
              return (
                <div key={t.id} style={{ display: "flex", flexDirection: "column", borderBottom: `1px solid ${C.border}` }}>

                  {/* Section bar */}
                  <button
                    onClick={() => toggle(t.id)}
                    style={{
                      display: "flex", alignItems: "center", justifyContent: "space-between",
                      padding: "11px 14px", cursor: "pointer", width: "100%",
                      background: isOpen ? C.bg3 : C.bg1,
                      border: "none",
                      borderLeft: `3px solid ${isOpen ? C.accent : "transparent"}`,
                      color: isOpen ? C.text : C.textMuted,
                      fontFamily: F, fontSize: 11, fontWeight: 700,
                      textAlign: "left", transition: "background 0.1s",
                    }}
                  >
                    <span>{t.icon} {t.label}</span>
                    <span style={{ fontSize: 9, color: isOpen ? C.accent : C.textDim }}>{isOpen ? "▲" : "▼"}</span>
                  </button>

                  {/* Section content */}
                  {isOpen && (
                    <div style={{ overflow: "auto", padding: "12px 14px", background: C.bg0, maxHeight: 420 }}>
                      <ConceptTabContent conceptId={concept.diagramId} tabId={t.id} />
                    </div>
                  )}

                </div>
              );
            })}
          </div>
        )}

      </div>
    </div>
  );
}
