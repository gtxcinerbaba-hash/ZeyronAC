<div align="center">

<!-- Title animation -->
<img src="https://readme-typing-svg.demolab.com/?font=Unbounded&weight=700&size=38&pause=1000&color=4C8BF5&center=true&vCenter=true&width=700&lines=ZeyronAC;Modern+Minecraft+Anti-Cheat" alt="ZeyronAC Typings" />

<p align="center">
  <b>ZeyronAC</b> — a server-side anti-cheat plugin for Minecraft servers that utilizes external, asynchronous server-side data processing.
  <br>
  The plugin acts as an ultra-lightweight connector, streaming player movement data to an external backend system for real-time analysis.
</p>

<!-- Links -->
<p align="center">
  <a href="https://discord.gg/b8YuxGUZaG">
    <img src="https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=flat-square&logo=discord&logoColor=white" />
  </a>
  <a href="https://github.com/gtxcinerbaba-hash/ZeyronAC/issues">
    <img src="https://img.shields.io/badge/Bugs-Report-ED4245?style=flat-square&logo=github" />
  </a>
  <a href="https://github.com/gtxcinerbaba-hash/ZeyronAC/issues">
    <img src="https://img.shields.io/badge/Features-Request-9B59B6?style=flat-square&logo=github" />
  </a>
</p>

<!-- Stats -->
<p align="center">
  <img src="https://img.shields.io/github/license/gtxcinerbaba-hash/ZeyronAC?style=flat-square&color=4C8BF5" />
  <img src="https://img.shields.io/github/stars/gtxcinerbaba-hash/ZeyronAC?style=flat-square&color=4C8BF5" />
  <img src="https://img.shields.io/github/forks/gtxcinerbaba-hash/ZeyronAC?style=flat-square&color=4C8BF5" />
</p>

</div>

---

## Key Features

| Feature | Description |
| :--- | :--- |
| **Remote Processing** | All heavy calculations and AI evaluations run on an external analysis server to prevent game server lag. |
| **FAST & PRO Models** | The FAST model handles instant detections, while the PRO model focuses on deep behavioral analysis. |
| **Cross-Server Analysis** | Effortlessly share violator data and reputation points between all of your connected servers. |
| **Flexible Customization** | Easily tweak threshold parameters and actions to fit various game modes and servers. |

---

## System Requirements

- Spigot / Paper / Purpur / Pufferfish or any compatible fork  
- Java 17+

---

## Installation & Setup

1. Download the latest `.jar` release from the **GitHub Releases** page.  
2. Place the downloaded `.jar` file into your server's `plugins/` directory.  
3. Start (or restart) your Minecraft server to generate configuration files.  
4. Configure the settings and establish your backend connection in the config file.  

---

## Commands

The main command is `/zeyronac`, which also has the aliases `/zr` and `/zron`.

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/zeyronac alerts` | `zeyronac.alerts` | Toggle alert notifications. |
| `/zeyronac monitor` | `zeyronac.alerts` | Toggle monitor mode (show all detections). |
| `/zeyronac suspects` | `zeyronac.alerts` | Open the suspects GUI. |
| `/zeyronac prob <player>` | `zeyronac.prob` | Show a player's current cheat probability. |
| `/zeyronac record <player> <CHEAT\|LEGIT\|UNLABELED> [comment]` | `zeyronac.collect` | Start recording player data to CSV. |
| `/zeyronac record stop <player>` | `zeyronac.collect` | Stop recording. |
| `/zeyronac record status` | `zeyronac.collect` | Show recording status. |
| `/zeyronac reload` | `zeyronac.reload` | Reload config and animations. |
| `/zeyronac reinstall` | `zeyronac.admin` | Add missing fields to config, messages, menu, and holograms. |
| `/zeyronac datastatus` | `zeyronac.admin` | Show data collection statistics. |
| `/zeyronac kicklist [page]` | `zeyronac.admin` | List of kicks issued by the AI anticheat. |
| `/zeyronac punish <player>` | `zeyronac.admin` | Execute max punishment on a player. |
| `/zeyronac profile <player>` | `zeyronac.admin` | View a player's profile. |
| `/zeyronac falsepositive restore <player>` | `zeyronac.admin` | Save 5,000 ticks of player data to CSV. |
| `/zeyronac status` | `zeyronac.admin` | Check API connection, latency, and daily stats. |
| `/zeyronac animation test <player> <animation>` | `zeyronac.admin` | Test a ban animation on a player. |
| `/zeyronac animation list` | `zeyronac.admin` | List available animations. |
| `/zeyronac animation reload` | `zeyronac.admin` | Reload only animations. |

---

## Permissions

| Permission | Default | Description |
| :--- | :--- | :--- |
| `zeyronac.admin` | op | Allows use of all ZeyronAC commands. |
| `zeyronac.alerts` | op | Allows toggling AI alerts and monitor mode. |
| `zeyronac.prob` | op | Allows probability tracking. |
| `zeyronac.reload` | op | Allows config reload. |
| `zeyronac.collect` | op | Allows data collection (record commands). |

---

## Credits & Acknowledgements

ZeyronAC is inspired by and derived from:
- **[SoMax1soft](https://github.com/SoMax1soft)** and the **MLSAC Team** — For the original development of the **MLSAC** project, which served as the core blueprint and foundation for ZeyronAC's networking and bridge plugin structure.

---

## License

This project is licensed under the GNU General Public License v3.0.

The project includes software distributed under GPLv3 and contains components derived from open-source projects licensed under compatible terms.
