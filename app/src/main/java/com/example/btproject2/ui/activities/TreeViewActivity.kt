package com.example.btproject2.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person

class TreeViewActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private var treeId = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tree_view)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)
        val webView = findViewById<WebView>(R.id.webViewTree)

        btnBack.setOnClickListener { finish() }

        // Setup WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

        // Load family data and render tree
        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                tvMemberCount.text = "${persons.size} members"
                val html = buildTreeHtml(persons)
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            },
            onFailure = {
                tvMemberCount.text = "Failed to load"
            }
        )
    }

    private fun buildTreeHtml(persons: List<Person>): String {
        val personMap = persons.associateBy { it.id }

        // Find root members (no parents)
        val roots = persons.filter { it.fatherId == null && it.motherId == null }

        // Build tree nodes as JSON
        val nodesJson = buildString {
            append("[")
            for ((index, person) in persons.withIndex()) {
                if (index > 0) append(",")
                val parentId = person.fatherId ?: person.motherId
                val parentName = if (parentId != null) {
                    val parent = personMap[parentId]
                    "\"${parent?.firstName ?: ""} ${parent?.lastName ?: ""}\""
                } else "null"

                append("{")
                append("\"id\":\"${person.id}\",")
                append("\"name\":\"${person.firstName} ${person.lastName}\",")
                append("\"gender\":\"${person.gender}\",")
                append("\"parentId\":${if (parentId != null) "\"$parentId\"" else "null"},")
                append("\"parentName\":$parentName,")
                append("\"birthDate\":\"${person.birthDate}\",")
                append("\"hasSpouse\":${person.spouseId != null}")
                append("}")
            }
            append("]")
        }

        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=0.8, user-scalable=yes">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, sans-serif;
    background: #FAFAF7;
    padding: 20px;
    min-width: 600px;
  }
  .tree-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0;
  }
  .generation {
    display: flex;
    justify-content: center;
    gap: 16px;
    margin-bottom: 8px;
    flex-wrap: wrap;
  }
  .gen-label {
    font-size: 11px;
    color: #B4B2A9;
    text-transform: uppercase;
    letter-spacing: 1px;
    margin-bottom: 8px;
    text-align: center;
    font-weight: 600;
  }
  .person-node {
    background: #FFFFFF;
    border: 1px solid #D3D1C7;
    border-radius: 14px;
    padding: 12px 16px;
    text-align: center;
    min-width: 120px;
    max-width: 160px;
    position: relative;
    cursor: pointer;
    transition: all 0.2s;
  }
  .person-node:hover {
    border-color: #1D9E75;
    box-shadow: 0 2px 8px rgba(29,158,117,0.15);
  }
  .person-node.root {
    background: #E8F4EE;
    border-color: #9FD9BA;
  }
  .person-node.male { border-left: 3px solid #185FA5; }
  .person-node.female { border-left: 3px solid #993556; }
  .avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: #1D9E75;
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 700;
    margin: 0 auto 6px;
  }
  .avatar.male { background: #185FA5; }
  .avatar.female { background: #993556; }
  .person-name {
    font-size: 12px;
    font-weight: 600;
    color: #2C2A24;
    line-height: 1.3;
  }
  .person-info {
    font-size: 10px;
    color: #888780;
    margin-top: 2px;
  }
  .connector {
    text-align: center;
    color: #9FD9BA;
    font-size: 20px;
    margin: 4px 0;
  }
  .couple-container {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .couple-link {
    font-size: 12px;
    color: #D85A30;
    font-weight: 600;
  }
  .empty-state {
    text-align: center;
    padding: 60px 20px;
    color: #888780;
  }
  .empty-state h3 {
    font-size: 16px;
    color: #2C2A24;
    margin-bottom: 8px;
  }
</style>
</head>
<body>
<script>
const persons = $nodesJson;

const container = document.createElement('div');
container.className = 'tree-container';
document.body.appendChild(container);

if (persons.length === 0) {
    container.innerHTML = '<div class="empty-state"><h3>No family members yet</h3><p>Add family members from the dashboard to see your tree.</p></div>';
} else {
    // Build parent-child map
    const childrenMap = {};
    const personById = {};
    persons.forEach(p => {
        personById[p.id] = p;
        const pid = p.parentId;
        if (pid) {
            if (!childrenMap[pid]) childrenMap[pid] = [];
            childrenMap[pid].push(p);
        }
    });

    // Find roots
    const roots = persons.filter(p => !p.parentId);

    // BFS to build generations
    const generations = [];
    let currentGen = roots.length > 0 ? roots : [persons[0]];
    const visited = new Set();

    while (currentGen.length > 0) {
        generations.push(currentGen);
        currentGen.forEach(p => visited.add(p.id));

        const nextGen = [];
        currentGen.forEach(p => {
            if (childrenMap[p.id]) {
                childrenMap[p.id].forEach(child => {
                    if (!visited.has(child.id)) {
                        nextGen.push(child);
                    }
                });
            }
        });
        currentGen = nextGen;
    }

    // Add unvisited persons
    const unvisited = persons.filter(p => !visited.has(p.id));
    if (unvisited.length > 0) {
        generations.push(unvisited);
    }

    // Render generations
    generations.forEach((gen, genIndex) => {
        const label = document.createElement('div');
        label.className = 'gen-label';
        label.textContent = 'Generation ' + (genIndex + 1);
        container.appendChild(label);

        const row = document.createElement('div');
        row.className = 'generation';

        gen.forEach(person => {
            const node = document.createElement('div');
            const isRoot = !person.parentId;
            const genderClass = person.gender.toLowerCase() === 'female' ? 'female' : 'male';
            node.className = 'person-node ' + genderClass + (isRoot ? ' root' : '');

            const initials = (person.name || '??').split(' ').map(n => n[0] || '').join('').toUpperCase().substring(0, 2);

            node.innerHTML =
                '<div class="avatar ' + genderClass + '">' + initials + '</div>' +
                '<div class="person-name">' + person.name + '</div>' +
                '<div class="person-info">' + person.gender +
                (person.birthDate ? ' · ' + person.birthDate : '') + '</div>';

            row.appendChild(node);
        });

        container.appendChild(row);

        if (genIndex < generations.length - 1) {
            const conn = document.createElement('div');
            conn.className = 'connector';
            conn.textContent = '│';
            container.appendChild(conn);
        }
    });
}
</script>
</body>
</html>
        """.trimIndent()
    }
}