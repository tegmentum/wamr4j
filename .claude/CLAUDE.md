# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## PROJECT OVERVIEW

This is **Claude Code PM (CCPM)** - a workflow enhancement system for managing complex software development projects using AI agents and GitHub integration. This is NOT a traditional software project but a meta-tool that sits on top of other projects to provide structured development workflows.

**Key Understanding**: This repository contains no application source code - it's a project management framework that uses:
- Shell scripts for command execution
- Markdown files with frontmatter for command definitions
- Specialized AI agents for context preservation
- GitHub Issues as the primary database
- Git worktrees for parallel development isolation

## DEVELOPMENT COMMANDS

Since this is a workflow tool, there are no traditional build/test/lint commands. Instead, use these PM commands:

### Essential Commands
- `/pm:init` - Install dependencies and configure GitHub integration
- `/pm:help` - Show all available PM commands
- `/pm:status` - Overall project dashboard
- `/pm:validate` - Check system integrity

### Testing Workflow Tools
- `/testing:prime` - Configure project's testing setup (adapts to target project)
- `/testing:run` - Execute tests via test-runner agent

### Context Management
- `/context:create` - Initialize project-wide context
- `/context:prime` - Load context for current work
- `/context:update` - Refresh context with latest changes

## ARCHITECTURE

**Core Principle**: Agent-based architecture for context optimization

```
.claude/
├── agents/           # Specialized AI agents (code-analyzer, file-analyzer, test-runner, parallel-worker)
├── commands/         # Command definitions as markdown files with frontmatter
├── context/          # Project-wide context files
├── epics/           # Local workspace for epic management (gitignored)
├── prds/            # Product Requirements Documents
├── rules/           # Development rules and coding standards
└── scripts/         # Shell scripts for command execution
```

**Key Architectural Patterns**:
- **Command Pattern**: Commands are markdown files with frontmatter specifying allowed tools
- **Agent Specialization**: Different agents for different tasks (UI, API, database, testing)
- **Context Preservation**: Heavy lifting done by agents, main thread stays clean
- **GitHub Integration**: Uses GitHub Issues as source of truth for project state

## WORKFLOW PHASES

1. **PRD Creation** (`/pm:prd-new`) - Comprehensive brainstorming and requirements documentation
2. **Epic Planning** (`/pm:prd-parse`) - Transform PRD into technical implementation plan
3. **Task Decomposition** (`/pm:epic-decompose`) - Break epic into actionable tasks
4. **GitHub Sync** (`/pm:epic-sync` or `/pm:epic-oneshot`) - Push to GitHub Issues
5. **Parallel Execution** (`/pm:issue-start`) - Deploy specialized agents for implementation

## CORE DEVELOPMENT PRINCIPLES

**No Vibe Coding**: Every line of code must trace back to a specification
- PRD → Epic → Task → Issue → Code → Commit
- Full traceability from requirements to production
- No assumptions, no shortcuts

**Context Optimization**: Always use sub-agents for heavy analysis
- Main conversation stays strategic and clean
- Agents handle implementation details in isolation
- Prevents context pollution and maintains coherence

> Think carefully and implement the most concise solution that changes as little code as possible.

## USE SUB-AGENTS FOR CONTEXT OPTIMIZATION

### 1. Always use the file-analyzer sub-agent when asked to read files.
The file-analyzer agent is an expert in extracting and summarizing critical information from files, particularly log files and verbose outputs. It provides concise, actionable summaries that preserve essential information while dramatically reducing context usage.

### 2. Always use the code-analyzer sub-agent when asked to search code, analyze code, research bugs, or trace logic flow.

The code-analyzer agent is an expert in code analysis, logic tracing, and vulnerability detection. It provides concise, actionable summaries that preserve essential information while dramatically reducing context usage.

### 3. Always use the test-runner sub-agent to run tests and analyze the test results.

Using the test-runner agent ensures:

- Full test output is captured for debugging
- Main conversation stays clean and focused
- Context usage is optimized
- All issues are properly surfaced
- No approval dialogs interrupt the workflow

## Philosophy

### Error Handling

- **Fail fast** for critical configuration (missing text model)
- **Log and continue** for optional features (extraction model)
- **Graceful degradation** when external services unavailable
- **User-friendly messages** through resilience layer

### Testing

- Always use the test-runner agent to execute tests.
- Do not use mock services for anything ever.
- Do not move on to the next test until the current test is complete.
- If the test fails, consider checking if the test is structured correctly before deciding we need to refactor the codebase.
- Tests to be verbose so we can use them for debugging.


## Tone and Behavior

- Criticism is welcome. Please tell me when I am wrong or mistaken, or even when you think I might be wrong or mistaken.
- Please tell me if there is a better approach than the one I am taking.
- Please tell me if there is a relevant standard or convention that I appear to be unaware of.
- Be skeptical.
- Be concise.
- Short summaries are OK, but don't give an extended breakdown unless we are working through the details of a plan.
- Do not flatter, and do not give compliments unless I am specifically asking for your judgement.
- Occasional pleasantries are fine.
- Feel free to ask many questions. If you are in doubt of my intent, don't guess. Ask.

## ABSOLUTE RULES:

- NO PARTIAL IMPLEMENTATION
- NO SIMPLIFICATION : no "//This is simplified stuff for now, complete implementation would blablabla"
- NO CODE DUPLICATION : check existing codebase to reuse functions and constants Read files before writing new functions. Use common sense function name to find them easily.
- NO DEAD CODE : either use or delete from codebase completely
- IMPLEMENT TEST FOR EVERY FUNCTIONS
- NO CHEATER TESTS : test must be accurate, reflect real usage and be designed to reveal flaws. No useless tests! Design tests to be verbose so we can use them for debuging.
- NO INCONSISTENT NAMING - read existing codebase naming patterns.
- NO OVER-ENGINEERING - Don't add unnecessary abstractions, factory patterns, or middleware when simple functions would work. Don't think "enterprise" when you need "working"
- NO MIXED CONCERNS - Don't put validation logic inside API handlers, database queries inside UI components, etc. instead of proper separation
- NO RESOURCE LEAKS - Don't forget to close database connections, clear timeouts, remove event listeners, or clean up file handles
