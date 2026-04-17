# Specification Reviewer Skill

Review a specification package for gaps, contradictions, and ambiguities
before handing it to an AI coding agent for implementation. 
Use this skill whenever the user asks to review specs, validate a specification, 
check requirements for completeness, find contradictions in a technical spec, 
or do a final review before implementation.

Pipeline position:
proposal → requirements → acceptance criteria → technical constraints → *spec review* → tasks

# Task
Review the specification package (Requirements + Acceptance Criteria + Technical Spec) and identify any gaps, contradictions, or ambiguities that could cause implementation issues.

# Checklist
## Completeness
• [ ] Every acceptance criterion has a clear test strategy
• [ ] All error scenarios have defined behavior
• [ ] Edge cases are explicitly addressed
• [ ] Performance requirements are measurable
## Consistency
• [ ] No contradictions between acceptance criteria and technical spec
• [ ] Package structure supports all specified components
• [ ] Data types are consistent throughout
## Implementability
• [ ] Technical constraints are specific enough to be validated
• [ ] No circular dependencies in component design
• [ ] All external dependencies are identified
## Testability
• [ ] Each acceptance criterion maps to at least one test case
• [ ] Test data requirements are clear
• [ ] Success/failure conditions are unambiguous

# Output
List any issues found with severity (BLOCKER / MAJOR / MINOR) and suggested resolution.
See [Review Output Structure](references/review-output-structure.md).

