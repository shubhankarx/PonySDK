PonySDK Documentation Strategy & Plan

Table of Contents

Documentation Charter

Repository File Index

Phase-by-Phase Plan

Integrating Daily Activity Logs

Templates & Examples

Next Steps

Documentation Charter




Goals

Onboard new contributors to PonySDK in 1–2 days by providing clear setup instructions and architecture overviews.

Surface design rationale (the “why”) behind key components so maintainers can make informed changes quickly.

Facilitate troubleshooting by capturing common errors, fixes, and FAQs.

Document Types

System & Architecture Overview: High-level module diagrams and data flow descriptions.

Quick-start & Setup Guides: Step-by-step instructions for local development and sample projects.

API Overviews: Package/module walkthroughs with code examples.

Troubleshooting & FAQs: User stories and solutions extracted from daily logs.

Architecture Decision Records (ADRs): ‘Context → Decision → Alternatives → Consequences’ for major technical choices.

Workflow Playbooks: Release, branching, and CI/CD recipes.



Process & Tools

Docs-as-Code: All .md files stored in docs/ directory under version control.

Site Generation: MkDocs powered by a mkdocs.yml config for navigation and theming.

CI Enforcement: Markdown lint and MkDocs build checks on pull requests; PR template reminds authors to update docs.

Style Guide: Consistent headings, terminology, and formatting enforced via markdownlint.

Repository File Index

Below are the key source files identified as most impactful based on recent work and daily logs:

ponysdk/src/main/java/com/ponysdk/core/terminal/socket/
  • WebSocket.java                # Server-side WebSocket implementation
  • WebSocketClient.java          # Client-side reader & update dispatch

ponysdk/src/main/java/com/ponysdk/core/server/websocket/
  • ServerToClientModel.java      # Enum of messages sent to client
  • ClientToServerModel.java      # Enum of client-to-server messages
  • ModelValuePair.java           # Represents (model,value) pairs in dictionary
  • ModelValueDictionary.java     # Maintains the compression dictionary

ponysdk/src/main/java/com/ponysdk/driver/
  • WebsocketClient.java          # Driver-side WebSocket wrapper

ponysdk/src/main/java/com/ponysdk/core/server/application/
  • UIContext.java                # Manages server-side UI sessions

ponysdk/src/main/java/com/ponysdk/core/server/application/
  • UIBuilder.java                # Builds UI updates from binary frames
  • UIFactory.java                # Helper for creating UI components

util/
  • ReaderBuffer.java             # Reads BinaryModel streams
  • PTInstruction.java            # Builds client request payloads

docs/                             # Documentation root (to create)
  • mkdocs.yml                    # Site config
  • index.md                      # Landing page
  • quick-reference/              # Task-focused guides
  • api/                          # API overview pages
  • playbooks/                    # Workflow recipes
  • adr/                          # Architecture Decision Records

File list focuses on classes modified or referenced heavily in daily logs.

Phase-by-Phase Plan

Phase

Objectives

Deliverables

Phase 1 – Planning

Define audience, scope, tools

Charter doc; file index; repo scaffold

Phase 2 – Research & Gather

Parse daily logs; audit existing docs; conduct interviews

Structured log spreadsheet; gap analysis

Phase 3 – Template Design

Create templates for QRGs, ADRs, playbooks

docs/quick-reference/template.md;docs/adr/template.md;docs/playbooks/template.md

Phase 4 – Drafting

Author QRGs & ADRs using real-world examples

Draft guides: setup.md, websocket.md, release.md;adr-0001.md, adr-0002.md

Phase 5 – Inline Annotation

Add docstrings/comments in key classes

PRs updating WebSocket.java, UIBuilder.java, etc.

Phase 6 – Review & Iterate

Peer review; usability tests

Feedback report; revised docs

Phase 7 – Publication

Deploy MkDocs site; announce & onboard team

Live docs URL; announcement materials

Phase 8 – Maintenance

Quarterly audits; CI doc checks

Audit schedule; CI lint rules

Integrating Daily Activity Logs

Leverage the Dailio copy.MD entries to ground docs in actual developer activities:

Collect & Structure LogsParse entries (Days 1–12) into a table with date, activity, and affected files citeturn1file2.

Extract User Stories

“As a developer, I need to implement dictionary compression in WebSocket communication” – from Day 5 logs citeturn1file2.

“As a maintainer, I encountered errors in WebSocketClient.java and UIContext.java and need debugging tips” – from Day 6 logs citeturn1file3.

“As a reviewer, I want to understand how UIBuilder.processUpdateWithDictionary dispatches operations” – from Day 28 logs citeturn1file1.

Map to Doc Types

Dictionary compression story → QRG or Troubleshooting doc.

Error/debugging story → FAQ entry under Debugging.

Dispatch logic story → API overview section with annotated code snippet citeturn1file4.

Embed Code ExamplesInclude vetted snippets (e.g., processUpdateWithDictionary, dispatchOperation) in docs with inline explanations citeturn1file5.

Templates & Examples

Quick Reference Guide (QRG)

# Quick Reference: Dictionary Compression Setup

**Purpose:** Enable or disable the WebSocket dictionary feature for testing.

## Prerequisites
- PonySDK built locally

## Steps
1. In `WebSocket.java`, toggle compression:
   ```java
   webSocket.setDictionaryEnabled(false);

Rebuild and run sample:

./gradlew :sample:run

Verify compressed frames in logs.

Common Pitfalls

Forgetting to rebuild after code changes

Missing DICT_UPDATE enum in ServerToClientModel.java

See Also

Troubleshooting: WebSocket dictionary errors


### Architecture Decision Record (ADR)

```markdown
# ADR 0001: Dictionary Compression in WebSocket

**Date:** 2025-04-07  

## Context
Identified repeated model-value patterns in WebSocket frames causing bandwidth waste (Day 5 logs) citeturn1file2.

## Decision
Implement server-side dictionary mapping (`ModelValueDictionary.java`) and client resolution (`ClientModelTracker.handleDictUpdate`).

## Alternatives
1. No compression (baseline)
2. Protobuf-based compression

## Consequences
- ~30% bandwidth reduction
- Added complexity in dictionary sync

Next Steps

Phase 2: Run the log parser; complete gap analysis by 2025-05-12.

Phase 3: Finalize templates with real examples by 2025-05-15.

Phase 4: Draft the first QRG (setup.md) and ADR (adr-0001.md) by 2025-05-20.

Phase 5: Open PRs for code annotations in key classes by 2025-05-25.

Last updated: 2025-05-09

