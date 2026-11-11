# Changelog

## [0.2.2](https://github.com/quokkify/archgate/compare/v0.2.1...v0.2.2) (2026-11-11)


### 📦 Dependencies

* **github-actions:** update actions/setup-java action to v6.2.0 ([#52](https://github.com/quokkify/archgate/issues/52)) ([4e2fc01](https://github.com/quokkify/archgate/commit/4e2fc015e0a1873caba5ecf87642ee8e4fbeead0))
* **gradle-wrapper:** update gradle to v9.9.0 ([#53](https://github.com/quokkify/archgate/issues/53)) ([380cffd](https://github.com/quokkify/archgate/commit/380cffdd881ce7d493a701a0d5633aa6f2b3d98b))
* **gradle:** update dev.quokkify:testng to v0.14.0 ([#54](https://github.com/quokkify/archgate/issues/54)) ([8d8ec8d](https://github.com/quokkify/archgate/commit/8d8ec8ddc551950510f61d6b279c84935450c1c7))


### 🧹 Chores

* **deps:** move Querydsl test fixtures to the OpenFeign fork ([#50](https://github.com/quokkify/archgate/issues/50)) ([1778651](https://github.com/quokkify/archgate/commit/177865166eddb851a9f1eba099c43fbd2225cb8c))
* **template:** update shared project template ([#55](https://github.com/quokkify/archgate/issues/55)) ([c6de07b](https://github.com/quokkify/archgate/commit/c6de07b350c2870f7babc3df5aa6647534543140))

## [0.2.1](https://github.com/quokkify/archgate/compare/v0.2.0...v0.2.1) (2026-11-07)

<!-- project-toolkit:release-notes v2 -->
**At a glance:** 1 fixed · 1 internal

### 🐛 Fixed

- **rules:** report use of a deprecated class in NoDeprecatedApiUsageRule ([#49](https://github.com/quokkify/archgate/pull/49))

<details><summary>Internal changes (1 PR)</summary>

**Maintenance**

- **template:** update shared project template ([#47](https://github.com/quokkify/archgate/pull/47))

</details>

## [0.2.0](https://github.com/quokkify/archgate/compare/v0.1.2...v0.2.0) (2026-11-07)

<!-- project-toolkit:release-notes v2 -->
**At a glance:** 1 new · 7 internal

### ✨ New

- check the non-empty scopes of main + test rules ([#44](https://github.com/quokkify/archgate/pull/44))

<details><summary>Internal changes (7 PRs)</summary>

**Dependencies**

- com.diffplug.spotless:spotless-plugin-gradle 8.10.4 → 8.11.0 ([#41](https://github.com/quokkify/archgate/pull/41))
- gradle/actions v6.4.0 → v6.4.1 ([#38](https://github.com/quokkify/archgate/pull/38))
- io.github.classgraph:classgraph 4.8.199 → 5.0.2 · major ([#27](https://github.com/quokkify/archgate/pull/27))
- quokkify/ci-kit v4.1.0 → v4.2.0 ([#40](https://github.com/quokkify/archgate/pull/40))

**Maintenance**

- run tests as one job per Gradle project ([#37](https://github.com/quokkify/archgate/pull/37))
- stop Renovate from raising the Maven API floor ([#42](https://github.com/quokkify/archgate/pull/42))
- **template:** update shared project template ([#45](https://github.com/quokkify/archgate/pull/45))

</details>

## [0.1.2](https://github.com/quokkify/archgate/compare/v0.1.1...v0.1.2) (2026-10-29)


### 🐛 Bug Fixes

* **ci:** collect Allure results into one directory so the report is not empty ([#35](https://github.com/quokkify/archgate/issues/35)) ([7163e2f](https://github.com/quokkify/archgate/commit/7163e2f593601df3dfdda1e4f6d717de80384d3d))
* **publish:** wait up to an hour for Maven Central to publish ([#31](https://github.com/quokkify/archgate/issues/31)) ([5b1b82f](https://github.com/quokkify/archgate/commit/5b1b82f13d75c6487f7d775bc229b180460dd822))

## [0.1.1](https://github.com/quokkify/archgate/compare/v0.1.0...v0.1.1) (2026-10-29)


### 🐛 Bug Fixes

* **publish:** fail fast on missing secrets and pass the optional signing key ID ([#29](https://github.com/quokkify/archgate/issues/29)) ([48e89b2](https://github.com/quokkify/archgate/commit/48e89b2d384b46c06031244c07cf877803b148bd))

## 0.1.0 (2026-10-28)


### ✨ Features

* port ArchGate to Gradle with Maven and Gradle plugins ([5e6a14a](https://github.com/quokkify/archgate/commit/5e6a14a8d2a5d48d0ee4908e752e28ba6b4187f5))


### 📦 Dependencies

* **gradle-wrapper:** update gradle to v9.8.1 ([#6](https://github.com/quokkify/archgate/issues/6)) ([1e8b32d](https://github.com/quokkify/archgate/commit/1e8b32df510894fbe056d9ebf06b4c0573eaacec))
* **gradle-wrapper:** update gradle to v9.8.2 ([#12](https://github.com/quokkify/archgate/issues/12)) ([2130a02](https://github.com/quokkify/archgate/commit/2130a022b72f92fa05fecbd2152bb9dc12cfbdcb))
* **gradle:** update org.apache.maven:apache-maven to v3.10.0 ([#11](https://github.com/quokkify/archgate/issues/11)) ([f018c75](https://github.com/quokkify/archgate/commit/f018c7583a709651f21600475a7c436d7355af9a))


### 🧹 Chores

* **build:** resolve maven-plugin-development from the version catalog ([#9](https://github.com/quokkify/archgate/issues/9)) ([e3f2872](https://github.com/quokkify/archgate/commit/e3f2872457303c813fbe41cf2909c38f90c16e6e))
* **ci-kit:** hand off base, security, release, allure, renovate to modules ([#17](https://github.com/quokkify/archgate/issues/17)) ([0e3bcec](https://github.com/quokkify/archgate/commit/0e3bcec91b9d0a69cd030329c634eadc87ebc204))
* **ci-kit:** hand off validate to core module ([#5](https://github.com/quokkify/archgate/issues/5)) ([c2525a7](https://github.com/quokkify/archgate/commit/c2525a72d45970b2b193f4db765c8b40681fb308))
* initial commit ([65308fe](https://github.com/quokkify/archgate/commit/65308fe3e319d06a98c60390ca56ae180112a1b0))
* release 0.1.0 as the first version ([#14](https://github.com/quokkify/archgate/issues/14)) ([5c3e149](https://github.com/quokkify/archgate/commit/5c3e14933aeadf76afb69ef75612f20a00482e43))
* **template:** update shared project template ([#10](https://github.com/quokkify/archgate/issues/10)) ([003bf93](https://github.com/quokkify/archgate/commit/003bf931aebed7b1ff56f271d2b84a097dcdd841))
* **template:** update shared project template ([#13](https://github.com/quokkify/archgate/issues/13)) ([4a37799](https://github.com/quokkify/archgate/commit/4a377996831f0cb02e7dc543ebaef713a2eea214))
* **template:** update shared project template ([#16](https://github.com/quokkify/archgate/issues/16)) ([6dc69b3](https://github.com/quokkify/archgate/commit/6dc69b3f7579ad6addf5fc0b3c088bc4aebac414))
* **template:** update shared project template ([#18](https://github.com/quokkify/archgate/issues/18)) ([4f740a1](https://github.com/quokkify/archgate/commit/4f740a1236ed4507650089a3253db683c0cec008))
* **template:** update shared project template ([#19](https://github.com/quokkify/archgate/issues/19)) ([ef8c51f](https://github.com/quokkify/archgate/commit/ef8c51fe72edecfc5eb38e9b3959825d44d69164))
* **template:** update shared project template ([#20](https://github.com/quokkify/archgate/issues/20)) ([e787421](https://github.com/quokkify/archgate/commit/e787421ac6626e20b3d919c27248261637cbbc43))
* **template:** update shared project template ([#22](https://github.com/quokkify/archgate/issues/22)) ([563345d](https://github.com/quokkify/archgate/commit/563345d9fe055e40fb7774e74213a8b15dfd35f6))
* **template:** update shared project template ([#3](https://github.com/quokkify/archgate/issues/3)) ([d9407f1](https://github.com/quokkify/archgate/commit/d9407f1f47fa76b230c6b29975fd0ae4bfba4d8e))
* **template:** update shared project template ([#4](https://github.com/quokkify/archgate/issues/4)) ([05a9887](https://github.com/quokkify/archgate/commit/05a9887abe7686fc77eab5c196a7fac1583b50d1))
