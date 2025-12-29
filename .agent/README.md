# How to Provide Context to AI

This guide explains how to help the AI assistant understand your project better.

## ✅ What's Already Set Up

I've created the following structure for you:

```
.agent/
├── CONTEXT.md                      # Central architecture reference
└── workflows/
    ├── add-backend-endpoint.md     # How to add REST endpoints
    └── add-frontend-component.md   # How to add React components
```

## 🤖 How I Use This Context

### 1. **CONTEXT.md** - Always Referenced
When you ask me to work on this project, I will:
- Check `.agent/CONTEXT.md` for architecture principles
- Understand the two-layer table system
- Know the tech stack and versions
- Follow established design decisions
- Avoid known pitfalls

### 2. **Workflows** - Task-Specific Guidance
When you use slash commands like:
- `/add-backend-endpoint` → I'll read `workflows/add-backend-endpoint.md`
- `/add-frontend-component` → I'll read `workflows/add-frontend-component.md`

These workflows include step-by-step instructions with `// turbo` annotations for auto-running safe commands.

## 📝 How to Extend This System

### Adding a New Workflow

Create a file in `.agent/workflows/<workflow-name>.md`:

```markdown
---
description: Short description of what this workflow does
---

# Workflow Title

## Steps

### 1. First Step
Explain what to do...

// turbo
```bash
./gradlew build
```

### 2. Second Step
More instructions...
```

**Important**: 
- Use `// turbo` above commands that are safe to auto-run
- Use `// turbo-all` at the top if ALL commands should auto-run

### Updating Architecture Decisions

Edit `.agent/CONTEXT.md` and add to the **Key Design Decisions** section:

```markdown
### Decision N: Title
**Reason**: Why this decision was made  
**Solution**: What was implemented  
**Impact**: How it affects the system
```

### Adding Common Patterns

Add to `.agent/CONTEXT.md` under a relevant section:
- **Critical Files**: If it's about file organization
- **Development Workflows**: If it's about dev processes
- **Common Pitfalls**: If it's a mistake to avoid

## 🎯 Best Practices

### ✅ DO
- Update `CONTEXT.md` when making architectural decisions
- Create workflows for repetitive tasks
- Document "why" not just "what"
- Include code examples in workflows
- Mark safe commands with `// turbo`

### ❌ DON'T
- Document obvious implementation details
- Create workflows for one-off tasks
- Put sensitive data (passwords, keys) in these files
- Duplicate information across multiple files

## 💡 Example: Documenting a New Decision

You made a decision to use Redis for caching. Update `CONTEXT.md`:

```markdown
### Decision 5: Redis for Metadata Caching
**Reason**: Reduce database load for frequently accessed table metadata  
**Solution**: Added Redis cache with 5-minute TTL in MetadataService  
**Impact**: All metadata reads check cache first, invalidate on writes  
**Config**: `spring.redis.host=localhost:6379`
```

## 🔄 Workflow Update Example

You want me to follow a specific pattern when adding cache layers:

Create `.agent/workflows/add-cache-layer.md`:

```markdown
---
description: How to add Redis caching to a service method
---

# Steps

### 1. Add @Cacheable annotation
// ... instructions ...

### 2. Configure cache name in application.yml
// ... instructions ...

// turbo
### 3. Rebuild the application
```bash
./gradlew :data-manager-backend:build -x test
```
```

## 🚀 Quick Reference

| Task | Action |
|------|--------|
| Document architecture decision | Update `.agent/CONTEXT.md` → Key Design Decisions |
| Create repeatable workflow | Add `.agent/workflows/<name>.md` |
| Add design pattern | Update `.agent/CONTEXT.md` → relevant section |
| Document common mistake | Update `.agent/CONTEXT.md` → Common Pitfalls |
| Add code example | Include in relevant workflow |

## 📞 Telling Me About Updates

Just say things like:
- "I've updated the context document with our Redis decision"
- "Check the new workflow for database migrations"
- "The CONTEXT.md now has our authentication strategy"

I'll automatically read these files when working on tasks!

## 🔍 What I'll Do Automatically

Whenever you ask me to:
1. **Add a feature** → I'll check CONTEXT.md for architecture constraints
2. **Fix a bug** → I'll check Common Pitfalls section
3. **Refactor code** → I'll ensure it follows documented patterns
4. **Use a slash command** → I'll read the corresponding workflow

You don't need to remind me to check these files - I'll do it automatically! 🎉
