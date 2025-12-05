FROM grafana/grafana:8.1.6

# ��������� ��������
RUN grafana-cli plugins install grafana-piechart-panel

# ����������� ���������������� ������
COPY config.ini /etc/grafana/config.ini
COPY provisioning /etc/grafana/provisioning

# �������� ���������� ��� ��������� � �����������
COPY dashboards /var/lib/grafana/dashboards/

# ����� ��� ����� ���������� ����� �� ���������