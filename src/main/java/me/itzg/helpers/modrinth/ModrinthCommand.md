## Dependency resolution

Some rules and behaviors:

- project entry provided by user may reference a file that contains a list of project entries
- projects should be retrieved in bulk where possible, which is more feasible since only ID or slug is needed
- explicit project references given by user take precedence over dependencies
  - can override version type
  - can override loader
- avoid cycles
- a dependency ref that indicates version takes precedence over a ref that does not indicate version
- user may choose to download only required dependencies, optional ones also, or none at all
- user declares global allowed version type (release > beta > alpha)
- user declares global loader, such as paper, forge
- each Loader enum declares compatible loader types and those are hierarchical, such as pufferfish → paper → spigot

### Model and providers

```mermaid
classDiagram
    class Project
    class ProjectRef
    class ResolvedProject
    ResolvedProject *-- Project
    ResolvedProject *-- ProjectRef
    class Version
    class VersionDependency {
        +String projectId
        +String? versionId
    }
    Version *-- "*" VersionDependency
    
    class ModrinthApiClient {
        +getVersionFromId(versionId: String) Version
        +getVersionsForProject(String projectIdOrSlug, @Nullable Loader loader, String gameVersion) List~Version~
        +bulkGetProjects(projectRefs) List~ResolvedProject~
        +getProject(projectIdOrSlug: String) Project
    }
```

```mermaid
flowchart TD
    subgraph ModrinthApiClient 
        getVersionFromId
        getVersionsForProject
        bulkGetProjects
        getProject
    end
    subgraph model 
        Version
        Project
        VersionDependency
    end
    getVersionFromId[[getVersionFromId]]
    versionId --> getVersionFromId --> Version
    
    getVersionsForProject[[getVersionsForProject]]
    projectIdOrSlug --> getVersionsForProject
    loader -- optional --> getVersionsForProject
    gameVersion --> getVersionsForProject
    getVersionsForProject -- N --> Version
    
    bulkGetProjects[[bulkGetProjects]]
    projectRefs --> bulkGetProjects --> ResolvedProject
    optionalSlugs --> bulkGetProjects
    
    getProject[[getProject]]
    projectIdOrSlug --> getProject --> Project
```
