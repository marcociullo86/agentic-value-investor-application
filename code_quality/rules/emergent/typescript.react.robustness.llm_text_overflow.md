---
rule_id: typescript.react.robustness.llm_text_overflow
version: v1
tier: emergent
status: candidate
title: "LLM-generated text blocks include overflow protection"
applies_to:
  language: typescript
  framework: react
  context: [llm-output, user-generated-content]
severity_default: low
auto_fixable: true
metadata:
  created_at: "2026-06-03"
  author: "agent:cqrl"
  triggered_by: "TSK-301 iter-1"
---
# Regola (candidate — gate umano per promozione)

## Rationale

LLM-generated text rendered with `whitespace-pre-wrap` can contain long
unbroken token sequences (e.g. URLs, technical identifiers, numeric strings)
that overflow their container on narrow viewports. Adding `break-words` (Tailwind
`break-words` utility, which maps to `overflow-wrap: break-word`) prevents
horizontal overflow without altering normal whitespace behaviour.

## Detection hints

`whitespace-pre-wrap` on a `<p>` or `<div>` that renders server-provided or
LLM-generated text, without a co-located `break-words` or equivalent
`overflow-wrap` class.

## Fix pattern

```tsx
// before
<p className="whitespace-pre-wrap text-sm ...">
  {llmText}
</p>

// after
<p className="whitespace-pre-wrap break-words text-sm ...">
  {llmText}
</p>
```
