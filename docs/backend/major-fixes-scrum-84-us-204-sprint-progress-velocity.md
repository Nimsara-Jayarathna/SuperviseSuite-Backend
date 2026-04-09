# Backend Major Fixes: SCRUM-84 US-204 - Sprint Progress & Velocity

## Scope

This story refines Jira tab analytics rules for health + sprint metrics without changing Jira OAuth behavior.

## What was implemented

- Replaced hardcoded Jira tab analytics heuristics with configuration-backed values.
- Added analytics configuration model under `app.jira.analytics.*`.
- Updated health classification and sprint trend logic to read from config.

## Configurable rules

- `app.jira.analytics.recent-sprints-limit`
- `app.jira.analytics.backlog-growing-consecutive-weeks`
- `app.jira.analytics.high-priority-names`
- `app.jira.analytics.bug-type-names`

## Main backend components

- `JiraProperties` (analytics block)
- `DefaultJiraHealthClassifier`
- `JiraSprintProgressServiceImpl`
- `application.yaml` defaults for analytics env bindings

## Operational note

- This story only affects Jira tab data calculations (project health + sprint/velocity).
- Jira OAuth authorization/connection flows are intentionally unchanged.
